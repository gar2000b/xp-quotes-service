package ca.digilogue.xp.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ca.digilogue.xp.grpc.OhlcvServiceProto.OhlcvCandleResponse;
import ca.digilogue.xp.grpc.impl.OhlcvServiceClient;
import ca.digilogue.xp.model.QuoteResponse;

/**
 * Service for handling quote-related business logic.
 * Coordinates with the OHLCV Generator Service via gRPC to fetch candle data.
 */
@Service
public class QuotesService {

    private static final Logger log = LoggerFactory.getLogger(QuotesService.class);

    private final OhlcvServiceClient ohlcvServiceClient;

    public QuotesService(OhlcvServiceClient ohlcvServiceClient) {
        this.ohlcvServiceClient = ohlcvServiceClient;
    }

    /**
     * Gets the latest OHLCV candle for a given symbol.
     * 
     * @param symbol The trading symbol (e.g., "MEGA-USD", "AAPL")
     * @return QuoteResponse with OHLCV data, or null if not found
     */
    public QuoteResponse getLatestQuote(String symbol) {
        log.info("Fetching latest quote for symbol: {}", symbol);
        
        // Validate symbol format (basic validation)
        if (symbol == null || symbol.trim().isEmpty()) {
            log.warn("Invalid symbol provided: {}", symbol);
            return null;
        }
        
        // Call gRPC service to get latest candle
        OhlcvCandleResponse candleResponse = ohlcvServiceClient.getLatestCandle(symbol.trim());
        
        if (candleResponse == null) {
            log.warn("No OHLCV candle data found for symbol: {}", symbol);
            return null;
        }
        
        // Convert gRPC response to QuoteResponse
        // Timestamp is in nanoseconds since epoch, convert to Instant
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
        
        log.info("Successfully retrieved quote for symbol: {}", symbol);
        return quoteResponse;
    }
}
