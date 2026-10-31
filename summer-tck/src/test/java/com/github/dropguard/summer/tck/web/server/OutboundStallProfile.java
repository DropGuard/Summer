package com.github.dropguard.summer.tck.web.server;

import com.github.dropguard.summer.test.profile.SummerTestProfile;
import java.util.Map;

/** Shortens the outbound stall budget so stall tests close connections in well under a second. */
public class OutboundStallProfile implements SummerTestProfile {

    @Override
    public String name() {
        return "outbound-stall";
    }

    @Override
    public Map<String, Object> configOverrides() {
        return Map.of("server.outbound-send-timeout", 500);
    }
}
