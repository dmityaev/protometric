package ru.proto.metric;

import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by dmitry on 22.02.16.
 */
public class Helper {
    private static final Logger logger = Logger.getLogger(Generator.class.getName());

    // read file content Helper
    public static String readFile(String path, Charset encoding)
            throws IOException
    {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }

    // executor helper
    public static String executeCommand(String command) {
        StringBuffer output = new StringBuffer();
        Process p;
        try {
            p = Runtime.getRuntime().exec(command);
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = "";
            while (null != (line = reader.readLine())) {
                logger.info(line);
                output.append(line + "\n");
            }
        } catch (Exception e) {
            logger.error(e);
        }

        return output.toString();

    }

    public static String createCLICommand(String user, String schema, String host, String script){
//        psql -U dmitry -d test -h localhost -f init.sql
        return "psql -U " + user + " -d " + schema + " -h " + host + " -f " + script;
    }
}
