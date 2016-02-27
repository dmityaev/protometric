/**
 * Created by dmitry on 20.02.16.
 */
package ru.proto.metric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Generator {
// properties
    private static final Logger logger = Logger.getLogger(Generator.class.getName());

    private static final String PROPERTY_TARGET         = "metric.target";
    private static final String PROPERTY_FILE			= "conf/metric.cfg";
    private static final String PROPERTIES			    = "config";
    private static final String PROPERTY_DBDRIVER		= "metric.db.driver";
    private static final String PROPERTY_CONNECT		= "metric.db.url";
    private static final String PROPERTY_USER			= "metric.db.user";
    private static final String PROPERTY_PASS			= "metric.db.pass";
    private static final String PROPERTY_SCHEMA         = "metric.db.schema";
    private static final String PROPERTY_HOST           = "metric.db.host";
    private static final String PROPERTY_DB_INIT_SCRIPT	= "metric.db.init";

    private static final String PROPERTY_ENTRIES	    = "metric.entries";
    private static final String PROPERTY_BATCH_CHUNK    = "metric.batch.chunk";
//    private static final String PROPERTY_EVENT_DELAY    = "metric.event.delay";

    private static final String PROPERTY_USER_LIST      = "metric.user.list";
    private static final String PROPERTY_EVENT_LIST     = "metric.event.list";
    private static final String PROPERTY_RES_LIST       = "metric.resolutions.list";

    private static final String PROPERTY_ES_HOST    = "metric.elastic.host";
    private static final String PROPERTY_ES_PORT    = "metric.elastic.port";
    private static final String PROPERTY_ES_CLUSTER = "metric.elastic.cluster";
    private static final String PROPERTY_ES_INDEX   = "metric.elastic.index";
    private static final String PROPERTY_ES_DOCTYPE = "metric.elastic.doctype";



    // vars
    private static Connection conn   = null;

    private static String targetEngine      = "elastic";
    private static String userListFile      = "";
    private static String eventListFile     = "";
    private static String resolutionsFile   = "";

    private static String esHost        = "";
    private static String esPort        = "";
    private static String esClusterName = "";
    private static String esIndex       = "";
    private static String esDocType     = "";

    private static String databaseDriver    = "";
    private static String connectString	    = "";
    private static String userName	        = "";
    private static String password	        = "";
    private static String schemaName	    = "";
    private static String hostName	        = "";

    private static int batchChunk           = 1000;
    private static String dbInitScript      = "";

    private static int entryCount           = 1000;
//    private static int eventDelay           = 100;

    private static Map<String, List<String>>    users           = new HashMap<String, List<String>>();
    private static List<String>                 events          = new ArrayList<String>();
    private static List<Pair<Integer, Integer>> resolutions     = new ArrayList<Pair<Integer, Integer>>();


//    MAIN
    public static void main(String [ ] args) {
//        initialize
        try {
            loadProperties();
//        init database
            if (targetEngine.equals("postgres")) {
                connectToDatabase(connectString, userName, password, databaseDriver);
                initDatabase(dbInitScript);
            }

//       load user and session list
            loadUserList(userListFile);

//       load event list
            loadEventList(eventListFile);

//        load resolutions
            loadResolutions(resolutionsFile);

//       generate raw data
            logger.info("start filling log");
            fillRawLog(entryCount);
            logger.info("filling log done");
        } catch (IOException |
                SAXException|
                SQLException |
                ParserConfigurationException|
                ClassNotFoundException e) {
            logger.error(e);
        }
    }

    private static void fillRawLog(int entryCount) throws SQLException, IOException {
        if (targetEngine.equals("postgres")) {
            fillPostgresRawLog(entryCount);
        } else {
            fillElasticRawLog(entryCount);
        }
    }


    private static void fillElasticRawLog(int entryCount) throws IOException {

        Settings settings = Settings.settingsBuilder()
//                .put("client.transport.ignore_cluster_name", true)
                .put("cluster.name", esClusterName)
//                .put("name", "Sasquatch")
                .build();
        Client client = TransportClient
                .builder()
                .settings(settings)
                .build()
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(esHost), Integer.parseInt(esPort)));

        BulkRequest br = client.prepareBulk().request();

        Gson gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                .create();

        int i = 1;
        for (i = 1; i <= entryCount; i++) {
            RawEntry entry = generateEntry(i);

            br.add(client.prepareIndex(esIndex, esDocType, (new Integer(i)).toString()).setSource(gson.toJson(entry)).request());

            if((0 == (i % batchChunk)) || (i == entryCount)){
                ActionFuture<BulkResponse> future = client.bulk(br);
                BulkResponse response = future.actionGet();
                logger.info(" : " + response.getItems().length +
                        " entries inserted, failures: " +
                        response.hasFailures());
                if (i != entryCount) {
                    br = client.prepareBulk().request();
                }
            }
//            try {
//                Thread.sleep(ThreadLocalRandom.current().nextInt(0, eventDelay));
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//            }
        }
        logger.info("Entries inserted: " + (--i));
        client.close();
    }


    private static void fillPostgresRawLog(int entryCount) throws SQLException {
        String sqlStr = "INSERT INTO raw_log" +
                " (user_id, session_id, event, event_time)" +
                " VALUES (?, ?, ?, ?)";
            PreparedStatement prepStatement = conn.prepareStatement(sqlStr);
            int i;
            for (i = 0; i < entryCount; i++) {
                RawEntry entry = generateEntry(i);

                prepStatement.setString(1, entry.userID);
                prepStatement.setString(2, entry.sessionID);
                prepStatement.setString(3, entry.event);
                prepStatement.setTimestamp(4, Timestamp.from(entry.eventTime.toInstant()));

                prepStatement.addBatch();

                if(0 == (i % batchChunk)) {
                    prepStatement.executeBatch();
                    logger.info(" : " + i + " entries inserted");
                }
            }
            prepStatement.executeBatch();
            logger.info(" : " + i + " entries inserted");
    }


//    generate entry object
    private static RawEntry generateEntry(Integer id) {
        RawEntry entry = new RawEntry();
//        entry.id = id;
        Set<HashMap.Entry<String, List<String>>> entries = users.entrySet();
        Object[] arObj = entries.toArray();

        int i = ThreadLocalRandom.current().nextInt(0, users.size());
        entry.userID = ((HashMap.Entry<String, List<String>>) arObj[i]).getKey();

        List<String> sl = ((HashMap.Entry<String, List<String>>) arObj[i]).getValue();
        entry.sessionID = sl.get(ThreadLocalRandom.current().nextInt(0, sl.size()));

        entry.resolution = resolutions.get(ThreadLocalRandom.current().nextInt(0, resolutions.size()));

        entry.event = events.get(ThreadLocalRandom.current().nextInt(0, events.size()));
        entry.eventTime = Timestamp.from(ZonedDateTime.now().toInstant());
        return entry;
    }


    // load properties
    private static void loadProperties() throws IOException {
        String propFile = System.getProperty(PROPERTIES, PROPERTY_FILE);
        Properties p = new Properties();
        p.load(new FileInputStream(propFile));

        targetEngine = p.getProperty(PROPERTY_TARGET);
        if (targetEngine.equals("elastic")) { // elastic
            esHost          = p.getProperty(PROPERTY_ES_HOST);
            esPort          = p.getProperty(PROPERTY_ES_PORT);
            esClusterName   = p.getProperty(PROPERTY_ES_CLUSTER);
            esIndex         = p.getProperty(PROPERTY_ES_INDEX);
            esDocType       = p.getProperty(PROPERTY_ES_DOCTYPE);
        } else { // postgres
            databaseDriver = p.getProperty(PROPERTY_DBDRIVER);
            connectString = p.getProperty(PROPERTY_CONNECT);
            userName = p.getProperty(PROPERTY_USER);
            password = p.getProperty(PROPERTY_PASS);

            schemaName = p.getProperty(PROPERTY_SCHEMA);
            hostName = p.getProperty(PROPERTY_HOST);

            dbInitScript = p.getProperty(PROPERTY_DB_INIT_SCRIPT);
        }

        batchChunk = Integer.parseInt(p.getProperty(PROPERTY_BATCH_CHUNK));
        entryCount = Integer.parseInt(p.getProperty(PROPERTY_ENTRIES));
//        eventDelay = Integer.parseInt(p.getProperty(PROPERTY_EVENT_DELAY));

        userListFile = p.getProperty(PROPERTY_USER_LIST);
        eventListFile = p.getProperty(PROPERTY_EVENT_LIST);
        resolutionsFile = p.getProperty(PROPERTY_RES_LIST);
        // initialize logging
        PropertyConfigurator.configure(propFile);
    }

    // open database connection
    private static void connectToDatabase(
                String connectionString,
                String userName,
                String password,
                String driver) throws ClassNotFoundException, SQLException {
            Class.forName(driver);
            if (null != conn){
                conn.close();
            }
            conn = DriverManager.getConnection(connectionString, userName, password);
            if ((null != conn) && (!conn.isClosed())) {
                logger.info("database connection open");
            } else {
                logger.info("database connection is not open");
            }
    }

    // initialize database
    private static void initDatabase(String script) throws IOException {
            File f = new File(script);
            String path = f.getCanonicalPath();
            String command = Helper.createCLICommand(userName, schemaName, hostName, path);
            Helper.executeCommand(command);
    }

//    load user id list
    private static void loadUserList(String fileName) throws ParserConfigurationException, IOException, SAXException {
            File xmlFile = new File(fileName);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            NodeList nodeListUser = doc.getElementsByTagName("user");

            for (int i = 0; i < nodeListUser.getLength(); i++) {
                org.w3c.dom.Node nodeUser = nodeListUser.item(i);
                if (org.w3c.dom.Node.ELEMENT_NODE == nodeUser.getNodeType()) {
                    Element elUser = (Element) nodeUser;
                    String userId = elUser.getAttribute("id");
                    List<String> sessionList = new ArrayList<String>();

                    NodeList nodeListSession = elUser.getElementsByTagName("session");
                    for (int j = 0; j < nodeListSession.getLength(); j++) {
                        org.w3c.dom.Node nodeSession = nodeListSession.item(j);
                        if (org.w3c.dom.Node.ELEMENT_NODE == nodeSession.getNodeType()) {
                            Element elSession = (Element) nodeSession;
                            String sessionId = elSession.getAttribute("id");
                            sessionList.add(sessionId);
                        }
                    }
                    users.put(userId, sessionList);
                }
            }
    }

    //    load user id list
    private static void loadResolutions(String fileName) throws ParserConfigurationException, IOException, SAXException {
        File xmlFile = new File(fileName);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        NodeList nodeResolutions = doc.getElementsByTagName("resolution");

        for (int i = 0; i < nodeResolutions.getLength(); i++) {
            org.w3c.dom.Node nodeRes = nodeResolutions.item(i);
            if (org.w3c.dom.Node.ELEMENT_NODE == nodeRes.getNodeType()) {
                Element elRes = (Element) nodeRes;
                String x = elRes.getAttribute("x");
                String y = elRes.getAttribute("y");
                Pair<Integer, Integer> res = new Pair<Integer, Integer>(Integer.parseInt(x), Integer.parseInt(y));
                resolutions.add(res);
            }
        }
    }


    //    load event list
    private static void loadEventList(String fileName) throws IOException {
            String content = null;
            content = Helper.readFile(fileName, StandardCharsets.UTF_8);
            String[] split = content.split("\\n");
            for (int i = 0; i < split.length; i++) {
                events.add(split[i]);
            }
    }
}
