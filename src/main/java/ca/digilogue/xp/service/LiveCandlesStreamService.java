package ca.digilogue.xp.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ca.digilogue.xp.App;
import ca.digilogue.xp.grpc.OhlcvServiceGrpc;
import ca.digilogue.xp.grpc.OhlcvServiceProto;
import ca.digilogue.xp.model.QuoteResponse;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.devh.boot.grpc.client.inject.GrpcClient;

/**
 * Service that manages the gRPC stream subscription for live candles.
 * Handles connection, reconnection, and updates the static collection in App.
 */
@Service
public class LiveCandlesStreamService {

    private static final Logger log = LoggerFactory.getLogger(LiveCandlesStreamService.class);
    private static final long RECONNECT_DELAY_SECONDS = 5;

    @GrpcClient("ohlcv-generator-service")
    private OhlcvServiceGrpc.OhlcvServiceStub ohlcvServiceStub;

    private volatile boolean running = false;
    private volatile boolean shouldReconnect = true;
    private ScheduledExecutorService reconnectExecutor;

    @PostConstruct
    public void start() {
        log.info("Starting LiveCandlesStreamService");
        running = true;
        shouldReconnect = true;
        reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LiveCandlesStream-Reconnect");
            t.setDaemon(true);
            return t;
        });
        connect();
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping LiveCandlesStreamService");
        shouldReconnect = false;
        running = false;
        
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdown();
            try {
                if (!reconnectExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    reconnectExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                reconnectExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void connect() {
        if (!running || !shouldReconnect) {
            return;
        }

        log.info("Connecting to StreamAllLiveCandles");

        try {
            // Create the request
            OhlcvServiceProto.StreamAllLiveCandlesRequest request = 
                    OhlcvServiceProto.StreamAllLiveCandlesRequest.getDefaultInstance();
            
            // Create the response observer
            StreamObserver<OhlcvServiceProto.AllCandlesResponse> responseObserver = 
                    new StreamObserver<OhlcvServiceProto.AllCandlesResponse>() {
                        @Override
                        public void onNext(OhlcvServiceProto.AllCandlesResponse response) {
                            try {
                                // Convert protobuf candles to QuoteResponse map
                                Map<String, QuoteResponse> candles = new HashMap<>();
                                
                                for (OhlcvServiceProto.OhlcvCandleResponse candleResponse : response.getCandlesList()) {
                                    // Convert timestamp from nanoseconds to Instant
                                    long timestampNanos = candleResponse.getTimestamp();
                                    Instant timestamp = Instant.ofEpochSecond(
                                            timestampNanos / 1_000_000_000,
                                            timestampNanos % 1_000_000_000
                                    );
                                    
                                    QuoteResponse quoteResponse = new QuoteResponse(
                                            candleResponse.getSymbol(),
                                            candleResponse.getOpen(),
                                            candleResponse.getHigh(),
                                            candleResponse.getLow(),
                                            candleResponse.getClose(),
                                            candleResponse.getVolume(),
                                            timestamp
                                    );
                                    
                                    candles.put(candleResponse.getSymbol(), quoteResponse);
                                }
                                
                                // Update the static collection in App
                                App.updateLiveCandles(candles);
                                
                                log.debug("Received and updated {} live candles", candles.size());
                                
                            } catch (Exception e) {
                                log.error("Error processing stream response", e);
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            log.warn("Stream error occurred", t);
                            
                            if (running && shouldReconnect) {
                                scheduleReconnect();
                            }
                        }

                        @Override
                        public void onCompleted() {
                            log.info("Stream completed");
                            
                            if (running && shouldReconnect) {
                                scheduleReconnect();
                            }
                        }
                    };
            
            // Call the streaming method (server-side streaming returns void)
            ohlcvServiceStub.streamAllLiveCandles(request, responseObserver);
            
            log.info("Successfully connected to StreamAllLiveCandles");

        } catch (Exception e) {
            log.error("Error connecting to stream", e);
            if (running && shouldReconnect) {
                scheduleReconnect();
            }
        }
    }

    private void scheduleReconnect() {
        if (!running || !shouldReconnect) {
            return;
        }

        log.info("Scheduling reconnection in {} seconds", RECONNECT_DELAY_SECONDS);
        
        reconnectExecutor.schedule(() -> {
            if (running && shouldReconnect) {
                log.info("Attempting to reconnect to stream");
                connect();
            }
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
