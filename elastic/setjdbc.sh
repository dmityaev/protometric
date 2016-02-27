#!/bin/bash

# ES_HOME required to detect elasticsearch jars

export ES_HOME=/usr/share/elasticsearch

echo '
{
    "type" : "jdbc",
    "jdbc" : {
        "url" : "jdbc:postgresql://localhost:5432/test",
        "user" : "dmitry",
        "password" : "1qwer432",
        "sql" : "SELECT id, user_id, session_id, event_time, event FROM raw_log"
    }
}
' | java \
-cp "${ES_HOME}/lib/*:${ES_HOME}/plugins/jdbc/*" \
org.xbib.tools.Runner org.xbib.tools.JDBCImporter
