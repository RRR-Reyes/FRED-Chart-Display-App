import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB service for FRED data operations
 */
public class FREDMongoService {
    private static final String COLLECTION_NAME = "fred_series";
    private MongoCollection<Document> collection;
    
    public FREDMongoService() {
        initialize();
    }
    
    private void initialize() {
        try {
            MongoDatabase database = MongoConfig.getDatabase();
            if (database != null) {
                collection = database.getCollection(COLLECTION_NAME);
                System.out.println("✓ FRED MongoDB service initialized");
            } else {
                System.err.println("✗ MongoDB not available - FRED service disabled");
            }
        } catch (Exception e) {
            System.err.println("✗ Failed to initialize FRED MongoDB service: " + e.getMessage());
        }
    }
    
    /**
     * Save FRED data to MongoDB (upsert - update if exists, insert if new)
     */
    public boolean saveFREDData(FREDDataDocument data) {
        if (!isServiceAvailable()) return false;
        
        try {
            Document query = new Document("seriesId", data.getSeriesId());
            Document doc = data.toDocument();
            
            ReplaceOptions options = new ReplaceOptions().upsert(true);
            collection.replaceOne(query, doc, options);
            
            System.out.println("✓ FRED data saved for series: " + data.getSeriesId());
            return true;
            
        } catch (Exception e) {
            System.err.println("✗ Failed to save FRED data: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Retrieve FRED data by series ID
     */
    public FREDDataDocument getFREDData(String seriesId) {
        if (!isServiceAvailable()) return null;
        
        try {
            Document doc = collection.find(Filters.eq("seriesId", seriesId)).first();
            if (doc != null) {
                System.out.println("✓ FRED data retrieved for series: " + seriesId);
                return FREDDataDocument.fromDocument(doc);
            }
            
        } catch (Exception e) {
            System.err.println("✗ Failed to retrieve FRED data: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Get all available series IDs
     */
    public List<String> getAllSeriesIds() {
        List<String> seriesIds = new ArrayList<>();
        
        if (!isServiceAvailable()) return seriesIds;
        
        try {
            for (Document doc : collection.find()) {
                String seriesId = doc.getString("seriesId");
                if (seriesId != null) {
                    seriesIds.add(seriesId);
                }
            }
            
        } catch (Exception e) {
            System.err.println("✗ Failed to get series IDs: " + e.getMessage());
        }
        
        return seriesIds;
    }
    
    /**
     * Delete FRED data by series ID
     */
    public boolean deleteFREDData(String seriesId) {
        if (!isServiceAvailable()) return false;
        
        try {
            collection.deleteOne(Filters.eq("seriesId", seriesId));
            System.out.println("✓ FRED data deleted for series: " + seriesId);
            return true;
            
        } catch (Exception e) {
            System.err.println("✗ Failed to delete FRED data: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if series exists in database
     */
    public boolean seriesExists(String seriesId) {
        if (!isServiceAvailable()) return false;
        
        try {
            Document doc = collection.find(Filters.eq("seriesId", seriesId)).first();
            return doc != null;
            
        } catch (Exception e) {
            System.err.println("✗ Failed to check series existence: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get count of all series in database
     */
    public long getSeriesCount() {
        if (!isServiceAvailable()) return 0;
        
        try {
            return collection.countDocuments();
            
        } catch (Exception e) {
            System.err.println("✗ Failed to get series count: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Clear all FRED data from database
     */
    public boolean clearAllData() {
        if (!isServiceAvailable()) return false;
        
        try {
            collection.deleteMany(new Document());
            System.out.println("✓ All FRED data cleared from database");
            return true;
            
        } catch (Exception e) {
            System.err.println("✗ Failed to clear FRED data: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if MongoDB service is available
     */
    public boolean isServiceAvailable() {
        return MongoConfig.isConnected() && collection != null;
    }
    
    /**
     * Get service status information
     */
    public String getServiceStatus() {
        if (!MongoConfig.isConnected()) {
            return "MongoDB: Not connected";
        }
        
        if (collection == null) {
            return "MongoDB: Connected but collection not initialized";
        }
        
        long count = getSeriesCount();
        return String.format("MongoDB: Connected, %d series in database", count);
    }
}
