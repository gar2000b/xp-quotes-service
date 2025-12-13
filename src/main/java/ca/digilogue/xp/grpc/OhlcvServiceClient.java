package ca.digilogue.xp.grpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ca.digilogue.xp.grpc.OhlcvServiceProto.GetLatestCandleRequest;
import ca.digilogue.xp.grpc.OhlcvServiceProto.OhlcvCandleResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;

/**
 * gRPC client for communicating with the OHLCV Generator Service.
 * Handles calls to get latest OHLCV candle data.
 */
@Service
public class OhlcvServiceClient {

    private static final Logger log = LoggerFactory.getLogger(OhlcvServiceClient.class);

    @GrpcClient("ohlcv-generator-service")
    private OhlcvServiceGrpc.OhlcvServiceBlockingStub ohlcvServiceStub;

    /**
     * Gets the latest OHLCV candle for a given symbol.
     * 
     * @param symbol The trading symbol (e.g., "MEGA-USD")
     * @return The latest OHLCV candle data, or null if not found or error occurs
     */
    public OhlcvCandleResponse getLatestCandle(String symbol) {
        log.info("Calling gRPC GetLatestCandle for symbol: {}", symbol);
        
        try {
            GetLatestCandleRequest request = GetLatestCandleRequest.newBuilder()
                    .setSymbol(symbol)
                    .build();
            
            OhlcvCandleResponse response = ohlcvServiceStub.getLatestCandle(request);
            log.info("Successfully retrieved OHLCV candle for symbol: {}", symbol);
            return response;
            
        } catch (StatusRuntimeException e) {
            Status status = e.getStatus();
            if (status.getCode() == Status.Code.NOT_FOUND) {
                log.warn("OHLCV candle not found for symbol: {}", symbol);
            } else {
                log.error("gRPC error getting latest candle for symbol {}: {}", symbol, status, e);
            }
            return null;
        } catch (Exception e) {
            log.error("Unexpected error calling gRPC GetLatestCandle for symbol {}: {}", symbol, e.getMessage(), e);
            return null;
        }
    }
}
