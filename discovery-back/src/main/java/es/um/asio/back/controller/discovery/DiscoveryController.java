package es.um.asio.back.controller.discovery;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import es.um.asio.service.config.DataSourcesConfiguration;
import es.um.asio.service.exceptions.CustomDiscoveryException;
import es.um.asio.service.model.BasicAction;
import es.um.asio.service.model.appstate.ApplicationState;
import es.um.asio.service.model.relational.JobRegistry;
import es.um.asio.service.service.EntitiesHandlerService;
import es.um.asio.service.service.impl.CacheServiceImp;
import es.um.asio.service.service.impl.DataHandlerImp;
import es.um.asio.service.service.impl.JobHandlerServiceImp;
import es.um.asio.service.util.Utils;
import es.um.asio.service.validation.group.Create;
import io.swagger.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


/**
 * Message controller.
 */
@RestController
@RequestMapping(DiscoveryController.Mappings.BASE)
public class DiscoveryController {


    @Autowired
    ApplicationState applicationState;

    @Autowired
    CacheServiceImp cache;

    @Autowired
    EntitiesHandlerService entitiesHandlerService;

    @Autowired
    DataSourcesConfiguration dataSourcesConfiguration;

    @Autowired
    JobHandlerServiceImp jobHandlerServiceImp;

    @Autowired
    DataHandlerImp dataHandler;


    private static final Logger logger = LoggerFactory.getLogger(DiscoveryController.class);

    /**
     * Status.
     *
     * @return Get the App status
     */
    @GetMapping()
    @ApiOperation(value = "Get status of the Application", tags = "control")
    public ApplicationState status() {
        return applicationState;
    }


    /**
     * Get Entity Stats.
     *
     * @return Get the Entty Stats
     */
    @GetMapping(Mappings.ENTITY_STATS)
    //@Secured(Role.ANONYMOUS_ROLE)
    @ApiOperation(value = "Get Entity Stats", tags = "control")
    public Map<String, Object> getEntityStats(
            @ApiParam(name = "node", value = "um", defaultValue = "um", required = false)
            @RequestParam(required = false, defaultValue = "um") @Validated(Create.class) final String node,
            @ApiParam(name = "tripleStore", value = "The triple store", defaultValue = "sparql", required = false)
            @RequestParam(required = true, defaultValue = "sparql") @Validated(Create.class) final String tripleStore,
            @ApiParam(name = "className", value = "Class Name", required = false)
            @RequestParam(required = true) @Validated(Create.class) final String className
    ) {
        Map<String,Object> stats = new HashMap<>();
        stats.put("status", applicationState);
        stats.put("stats",cache.getStatsHandler().buildStats(node,tripleStore,className));
        return stats;
    }

    /**
     * Find similarities by Class Name.
     *
     * @return Get the Entty Stats
     */

    @PostMapping(Mappings.ENTITY_LINK)
    @ApiOperation(value = "Find Similarities between entities by class", tags = "search")
    public Map<String,Object> findEntityLinkByNodeTripleStoreAndClass(
            @ApiParam(name = "userId", value = "1", defaultValue = "1", required = true)
            @RequestParam(required = true, defaultValue = "1") @Validated(Create.class) final String userId,
            @ApiParam(name = "requestCode", value = "12345", defaultValue = "12345", required = true)
            @RequestParam(required = true, defaultValue = "12345") @Validated(Create.class) final String requestCode,
            @ApiParam(name = "node", value = "um", defaultValue = "um", required = false)
            @RequestParam(required = true, defaultValue = "um") @Validated(Create.class) final String node,
            @ApiParam(name = "tripleStore", value = "The triple store", defaultValue = "sparql", required = false)
            @RequestParam(required = true, defaultValue = "sparql") @Validated(Create.class) final String tripleStore,
            @ApiParam(name = "className", value = "Class Name", required = false)
            @RequestParam(required = true) @Validated(Create.class) final String className,
            @ApiParam(name = "doSynchronous", value = "Handle request as synchronous request", defaultValue = "false", required = false)
            @RequestParam(required = false, defaultValue = "false") @Validated(Create.class) final boolean doSynchronous,
            @ApiParam(name = "webHook", value = "Web Hook, URL Callback with response", required = false)
            @RequestParam(required = false) @Validated(Create.class) final String webHook,
            @ApiParam(name = "propague_in_kafka", value = "Propague result in Kafka", defaultValue = "true", required = false)
            @RequestParam(required = false, defaultValue = "true") @Validated(Create.class) final boolean propagueInKafka,
            @ApiParam(name = "linkEntities", value = "Search also in other Nodes and Triple Stores for link", defaultValue = "false", required = true)
            @RequestParam(required = true) @Validated(Create.class) final boolean linkEntities,
            @ApiParam(name = "applyDelta", value = "SearchindEntityLinkByEntityAndNodeTripleStoreAndClass only from last date in similar request", defaultValue = "true",required = true)
            @RequestParam(required = true) @Validated(Create.class) final boolean applyDelta
    ) {

        if (!doSynchronous && ((!Utils.isValidString(webHook) || !Utils.isValidURL(webHook)) && !propagueInKafka) ) {
            throw new CustomDiscoveryException("The request must be synchronous or web hook or/and propague in kafka must be valid" );
        }
        JobRegistry jobRegistry = jobHandlerServiceImp.addJobRegistryForClass(applicationState.getApplication(),userId,requestCode,node,tripleStore,className,doSynchronous,webHook,propagueInKafka,linkEntities,applyDelta);
        JsonObject jResponse = new JsonObject();
        jResponse.add("state",applicationState.toSimplifiedJson());
        if (jobRegistry!=null) {
            JsonObject jJobRegistry = jobRegistry.toSimplifiedJson();
            jJobRegistry.addProperty("userId", userId);
            jJobRegistry.addProperty("requestCode", requestCode);
            jResponse.add("response", jJobRegistry);
        } else {
            jResponse.addProperty("message","Application is not ready, please retry late");
        }
        return new Gson().fromJson(jResponse,Map.class);
    }

    /**
     * Get Entity Stats.
     *
     * @return Get the Entty Stats
     */
    @PostMapping(Mappings.ENTITY_LINK_ENTITY)
    @ApiOperation(value = "Find Similarities between entities by instance in body", tags = "search")
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = "object",
                    dataType = "TripleObject",
                    examples = @io.swagger.annotations.Example(
                            value = {
                                    @ExampleProperty(value = "’object‘：{'id': '1','className': 'Actividad','attributes':{" +
                                            "'codTipoActividad': '00',"+
                                            "'codTipoMoneda': 'EUR',"+
                                            "'fechaInicioActividad': '2008/04/28 00:00:00',"+
                                            "'idActividad': '5192',"+
                                            "'idGrupoActividades': '3470',"+
                                            "'idTercero': '146051',"+
                                            "'importeBase': '2400.0',"+
                                            "'importeRepercutido': '384.0',"+
                                            "'importeTotal': '2784.0',"+
                                            "'terceroConfidencial': 'N',"+
                                            "'titulo': 'INVESTIGACIÓN DE PLAN DE ACTUACIÓN PARA PREVENIR Y/O CONTROLAR UN BROTE DE BOTULISMO EN EL HONGO. VERANO 2008',"+
                                            "}" +
                                            "}", mediaType = "application/json")
                            }))
    })
    @ResponseBody
    public Map<String,Object> findEntityLinkByEntityAndNodeTripleStoreAndClass(
            @ApiParam(name = "userId", value = "1", defaultValue = "1", required = true)
            @RequestParam(required = true, defaultValue = "1") @Validated(Create.class) final String userId,
            @ApiParam(name = "requestCode", value = "12345", defaultValue = "12345", required = true)
            @RequestParam(required = true, defaultValue = "12345") @Validated(Create.class) final String requestCode,
            @ApiParam(name = "node", value = "um", defaultValue = "um", required = false)
            @RequestParam(required = false, defaultValue = "um") @Validated(Create.class) final String node,
            @ApiParam(name = "tripleStore", value = "The triple store", defaultValue = "sparql", required = false)
            @RequestParam(required = true, defaultValue = "sparql") @Validated(Create.class) final String tripleStore,
            @ApiParam(name = "className", value = "Class Name", required = true)
            @RequestParam(required = true) @Validated(Create.class) final String className,
            @ApiParam(name = "entityId", value = "12345", required = true)
            @RequestParam(required = true) @Validated(Create.class) final String entityId,
            @ApiParam(name = "doSynchronous", value = "false", required = false)
            @RequestParam(required = false, defaultValue = "false") @Validated(Create.class) final boolean doSynchronous,
            @ApiParam(name = "webHook", value = "Web Hook, URL Callback with response", required = false)
            @RequestParam(required = false) @Validated(Create.class) final String webHook,
            @ApiParam(name = "propague_in_kafka", value = "true", required = false)
            @RequestParam(required = false, defaultValue = "true") @Validated(Create.class) final boolean propagueInKafka,
            @ApiParam(name = "linkEntities", value = "true", required = true)
            @RequestParam(required = true) @Validated(Create.class) final boolean linkEntities,
            @NotNull @RequestBody final Object object
    ) {
        JSONObject jsonData = new JSONObject((LinkedHashMap) object);
        String jBodyStr = jsonData.toString();
        if (!doSynchronous && ((!Utils.isValidString(webHook) || !Utils.isValidURL(webHook)) && !propagueInKafka) ) {
            throw new CustomDiscoveryException("The request must be synchronous or web hook or/and propague in kafka must be valid" );
        }
        try {
            JobRegistry jobRegistry = jobHandlerServiceImp.addJobRegistryForInstance(
                    applicationState.getApplication(),
                    userId,
                    requestCode,
                    node,
                    tripleStore,
                    className,
                    entityId,
                    jBodyStr,
                    doSynchronous,webHook,
                    propagueInKafka,
                    linkEntities
            );
            JsonObject jResponse = new JsonObject();
            jResponse.add("state",applicationState.toSimplifiedJson());
            if (jobRegistry!=null) {
                JsonObject jJobRegistry = jobRegistry.toSimplifiedJson();
                jJobRegistry.addProperty("userId", userId);
                jJobRegistry.addProperty("requestCode", requestCode);
                jResponse.add("response", jJobRegistry);
            } else {
                jResponse.addProperty("message","Application is not ready, please retry late");
            }
            return new Gson().fromJson(jResponse,Map.class);
        } catch (Exception e) {
            throw new CustomDiscoveryException(e.getMessage());
        }
    }

    /**
     * Find similarities by Class Name.
     *
     * @return Get the Entty Stats
     */
    @PostMapping(Mappings.LOD_SEARCH)
    @ApiOperation(value = "Find Similarities between entities in cloud LOD", tags = "search")
    public Map<String,Object> findFindLinkInLODByNodeTripleStoreAndClass(
            @ApiParam(name = "userId", value = "1", defaultValue = "1", required = true)
            @RequestParam(required = true, defaultValue = "1") @Validated(Create.class) final String userId,
            @ApiParam(name = "requestCode", value = "12345", defaultValue = "12345", required = true)
            @RequestParam(required = true, defaultValue = "12345") @Validated(Create.class) final String requestCode,
            @ApiParam(name = "node", value = "um", defaultValue = "um", required = false)
            @RequestParam(required = true, defaultValue = "um") @Validated(Create.class) final String node,
            @ApiParam(name = "tripleStore", value = "The triple store", defaultValue = "sparql", required = false)
            @RequestParam(required = true, defaultValue = "sparql") @Validated(Create.class) final String tripleStore,
            @ApiParam(name = "className", value = "Class Name", required = false)
            @RequestParam(required = true) @Validated(Create.class) final String className,
            @ApiParam(name = "doSynchronous", value = "Handle request as synchronous request", defaultValue = "false", required = false)
            @RequestParam(required = false, defaultValue = "false") @Validated(Create.class) final boolean doSynchronous,
            @ApiParam(name = "webHook", value = "Web Hook, URL Callback with response", required = false)
            @RequestParam(required = false) @Validated(Create.class) final String webHook,
            @ApiParam(name = "propague_in_kafka", value = "Propague result in Kafka", defaultValue = "true", required = false)
            @RequestParam(required = false, defaultValue = "true") @Validated(Create.class) final boolean propagueInKafka,
            @ApiParam(name = "applyDelta", value = "SearchindEntityLinkByEntityAndNodeTripleStoreAndClass only from last date in similar request", defaultValue = "true",required = true)
            @RequestParam(required = true) @Validated(Create.class) final boolean applyDelta
    ) {

        if (!doSynchronous && ((!Utils.isValidString(webHook) || !Utils.isValidURL(webHook)) && !propagueInKafka) ) {
            throw new CustomDiscoveryException("The request must be synchronous or web hook or/and propague in kafka must be valid" );
        }
        JobRegistry jobRegistry = jobHandlerServiceImp.addJobRegistryForLOD(applicationState.getApplication(),userId,requestCode,node,tripleStore,className,doSynchronous,webHook,propagueInKafka,applyDelta);
        JsonObject jResponse = new JsonObject();
        jResponse.add("state",applicationState.toSimplifiedJson());
        if (jobRegistry!=null) {
            JsonObject jJobRegistry = jobRegistry.toSimplifiedJson();
            jJobRegistry.addProperty("userId", userId);
            jJobRegistry.addProperty("requestCode", requestCode);
            jResponse.add("response", jJobRegistry);
        } else {
            jResponse.addProperty("message","Application is not ready, please retry late");
        }
        return new Gson().fromJson(jResponse,Map.class);
    }

    /**
     * Get Entity Stats.
     *
     * @return Get the Entty Stats
     */
    @PostMapping(Mappings.ENTITY_CHANGE)
    @ApiOperation(value = "Notifications on changes over instances", tags = "control")
    public ResponseEntity<String> entityChange(
            @ApiParam(name = "node", value = "um", defaultValue = "um", required = true)
            @RequestParam(required = true, defaultValue = "um") @Validated(Create.class) final String node,
            @ApiParam(name = "tripleStore", value = "The triple store", defaultValue = "sparql", required = false)
            @RequestParam(required = true, defaultValue = "sparql") @Validated(Create.class) final String tripleStore,
            @ApiParam(name = "className", value = "Class Name", required = true)
            @RequestParam(required = true) @Validated(Create.class) final String className,
            @ApiParam(name = "entityLocalURI", required = true)
            @RequestParam(required = true) @Validated(Create.class) final String entityLocalURI,
            @ApiParam(name = "action", value = "", required = true)
            @RequestParam(required = true) @Validated(Create.class) final String action

    ) {
        if (BasicAction.fromString(action) == null) {
            new ResponseEntity<String>("Action not valid: "+ action+ ". Values allowed are [INSERT,UPDATE,DELETE]",HttpStatus.NOT_ACCEPTABLE);
        }
        if (applicationState.getAppState() != ApplicationState.AppState.INITIALIZED) {
            new ResponseEntity<String>("Application not initialized yet. The entity will be updated from cache on app start",HttpStatus.CONFLICT);
        }
        boolean result = false;
        try {
            CompletableFuture<Boolean> future = dataHandler.actualizeData(node, tripleStore, className, entityLocalURI, BasicAction.fromString(action));
            result = future.join();
        } catch (Exception e) {
            return new ResponseEntity<>("FAIL",HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (result)
            return new ResponseEntity<>("DONE",HttpStatus.ACCEPTED);
        else
            return new ResponseEntity<>("FAIL",HttpStatus.NOT_ACCEPTABLE);
    }

    @PostMapping(Mappings.RELOAD_CACHE)
    @ApiOperation(value = "Force reload cache from Triple Store Data", tags = "control")
    public ResponseEntity<String> doForceReloadCache() {
        try {
            if (applicationState.getAppState().getOrder() >= ApplicationState.AppState.INITIALIZED.getOrder()) {
                dataHandler.populateData();
                return new ResponseEntity<>("DONE",HttpStatus.OK);
            } else {
                return new ResponseEntity<>("IN PROCESS",HttpStatus.OK);
            }
        } catch (ParseException e) {
            logger.error("ParseException: {}",e.getMessage());
        } catch (IOException e) {
            logger.error("IOException: {}",e.getMessage());
        } catch (URISyntaxException e) {
            logger.error("URISyntaxException: {}",e.getMessage());
        }
        return new ResponseEntity<>("FAIL",HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Mappgins.
     */
    static final class Mappings {

        private Mappings(){}

        /**
         * Controller request mapping.
         */
        protected static final String BASE = "/discovery/";

        protected static final String STATUS = "/status";

        protected static final String ENTITY_STATS = "/entity/stats";

        protected static final String RELOAD_CACHE = "/cache/force-reload";

        protected static final String ENTITY_LINK = "/entity-link";

        protected static final String ENTITY_LINK_ENTITY = "/entity-link/instance";

        protected static final String ENTITY_CHANGE = "/entity/change";

        protected static final String LOD_SEARCH = "/lod/search";


    }
}
