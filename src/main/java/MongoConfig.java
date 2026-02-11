import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

/**
 * MongoDB configuration and connection management
 */
public class MongoConfig {
    private static final String DEFAULT_CONNECTION_STRING = "mongodb://localhost:27017";
    private static final String DEFAULT_DATABASE_NAME = "fred_data";
    
    private static MongoClient mongoClient;
    private static MongoDatabase database;
    
    /**
     * Initialize MongoDB connection with default settings
     */
    public static void initialize() {
        initialize(DEFAULT_CONNECTION_STRING, DEFAULT_DATABASE_NAME);
    }
    
    /**
     * Initialize MongoDB connection with custom settings
     */
    public static void initialize(String connectionString, String databaseName) {
        try {
            if (mongoClient != null) {
                mongoClient.close();
            }
            
            mongoClient = MongoClients.create(connectionString);
            database = mongoClient.getDatabase(databaseName);
            
            // Test connection
            database.runCommand(new org.bson.Document("ping", 1));
            System.out.println("✓ MongoDB connected successfully");
            
        } catch (Exception e) {
            System.err.println("✗ MongoDB connection failed: " + e.getMessage());
            mongoClient = null;
            database = null;
        }
    }
    
    /**
     * Get MongoDB database instance
     */
    public static MongoDatabase getDatabase() {
        if (database == null) {
            initialize(); // Initialize with defaults if not already done
        }
        return database;
    }
    
    /**
     * Get MongoDB client instance
     */
    public static MongoClient getClient() {
        if (mongoClient == null) {
            initialize(); // Initialize with defaults if not already done
        }
        return mongoClient;
    }
    
    /**
     * Check if MongoDB is connected
     */
    public static boolean isConnected() {
        return mongoClient != null && database != null;
    }
    
    /**
     * Close MongoDB connection
     */
    public static void close() {
        if (mongoClient != null) {
            mongoClient.close();
            mongoClient = null;
            database = null;
            System.out.println("MongoDB connection closed");
        }
    }
}
