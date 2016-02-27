package ru.proto.metric;

import java.sql.Timestamp;

/**
 * Created by dmitry on 20.02.16.
 */
public class RawEntry {
//    public Integer id;
    public String userID;
    public String sessionID;
    public Timestamp eventTime;
    public String event;
    public Pair<Integer, Integer> resolution;
}
