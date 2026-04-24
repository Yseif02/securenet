package com.securenet.common;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EpsLoadBalancerTest {

    @Test
    void nextHealthyUrl_prefersKnownHealthyLeader() throws Exception {
        EpsLoadBalancer lb = new EpsLoadBalancer(List.of(
                "http://localhost:9003",
                "http://localhost:9103"
        ));

        setLeader(lb, "http://localhost:9103");
        setHealthyUrls(lb, Set.of("http://localhost:9003", "http://localhost:9103"));

        assertEquals("http://localhost:9103", lb.nextHealthyUrl());
    }

    @Test
    void nextHealthyUrl_fallsBackWhenLeaderIsNotHealthy() throws Exception {
        EpsLoadBalancer lb = new EpsLoadBalancer(List.of(
                "http://localhost:9003"
        ));

        setLeader(lb, "http://localhost:9103");
        setHealthyUrls(lb, Set.of("http://localhost:9003"));

        assertEquals("http://localhost:9003", lb.nextHealthyUrl());
    }

    private static void setLeader(EpsLoadBalancer lb, String leaderUrl) throws Exception {
        Field field = EpsLoadBalancer.class.getDeclaredField("leaderUrl");
        field.setAccessible(true);
        field.set(lb, leaderUrl);
    }

    @SuppressWarnings("unchecked")
    private static void setHealthyUrls(LoadBalancer lb, Set<String> healthy) throws Exception {
        Field field = LoadBalancer.class.getDeclaredField("healthyUrls");
        field.setAccessible(true);
        Set<String> target = (Set<String>) field.get(lb);
        target.clear();
        target.addAll(healthy);
    }
}
