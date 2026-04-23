package com.securenet.common;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * EPS-specific load balancer that routes write traffic to the current
 * Raft leader whenever it can identify one.
 */
public class EpsLoadBalancer extends LoadBalancer {

    private static final Logger log = Logger.getLogger(EpsLoadBalancer.class.getName());

    private static final int LEADER_POLL_INTERVAL_MS = 1000;

    private final ServiceClient httpClient = new ServiceClient();
    private final ScheduledExecutorService leaderScheduler;

    private volatile String leaderUrl;

    public EpsLoadBalancer(List<String> instanceUrls) {
        super("EPS", instanceUrls);
        this.leaderScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lb-EPS-leader");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void start() {
        super.start();
        leaderScheduler.scheduleAtFixedRate(
                this::pollLeader,
                0, LEADER_POLL_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        leaderScheduler.shutdownNow();
        super.stop();
    }

    /**
     * Returns the current leader URL when known and healthy, otherwise falls
     * back to normal round-robin routing.
     */
    @Override
    public String nextHealthyUrl() {
        String cachedLeader = leaderUrl;
        if (cachedLeader != null && Boolean.TRUE.equals(getStatus().get(cachedLeader))) {
            log.fine("[LB:EPS] Selected Raft leader " + cachedLeader);
            return cachedLeader;
        }
        return super.nextHealthyUrl();
    }

    @SuppressWarnings("unchecked")
    private void pollLeader() {
        for (Map.Entry<String, Boolean> entry : getStatus().entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) continue;

            String url = entry.getKey();
            try {
                ServiceClient.ServiceResponse response = httpClient.get(url + "/eps/raft/status");
                if (!response.isSuccess()) continue;

                Map<String, Object> status = response.bodyAs(Map.class);
                if ("LEADER".equals(status.get("state"))) {
                    if (!Objects.equals(leaderUrl, url)) {
                        leaderUrl = url;
                        log.info("[LB:EPS] Raft leader discovered: " + url
                                + " nodeId=" + status.get("nodeId"));
                    }
                    return;
                }
            } catch (Exception e) {
                log.fine("[LB:EPS] Leader poll failed for " + url + ": " + e.getMessage());
            }
        }
        leaderUrl = null;
    }
}
