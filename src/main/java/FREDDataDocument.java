import org.bson.Document;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB document model for FRED time series data
 */
public class FREDDataDocument {
    private String seriesId;
    private String title;
    private String frequency;
    private String units;
    private String lastUpdated;
    private List<ObservationDocument> observations;
    private LocalDate firstDate;
    private LocalDate lastDate;
    private long createdAt;
    
    public FREDDataDocument() {
        this.observations = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
    }
    
    public FREDDataDocument(String seriesId, String title, String frequency, String units) {
        this();
        this.seriesId = seriesId;
        this.title = title;
        this.frequency = frequency;
        this.units = units;
    }
    
    // Convert from MongoDB Document
    public static FREDDataDocument fromDocument(Document doc) {
        FREDDataDocument data = new FREDDataDocument();
        data.seriesId = doc.getString("seriesId");
        data.title = doc.getString("title");
        data.frequency = doc.getString("frequency");
        data.units = doc.getString("units");
        data.lastUpdated = doc.getString("lastUpdated");
        data.createdAt = doc.getLong("createdAt");
        
        if (doc.getString("firstDate") != null) {
            data.firstDate = LocalDate.parse(doc.getString("firstDate"));
        }
        if (doc.getString("lastDate") != null) {
            data.lastDate = LocalDate.parse(doc.getString("lastDate"));
        }
        
        @SuppressWarnings("unchecked")
        List<Document> obsDocs = (List<Document>) doc.get("observations");
        if (obsDocs != null) {
            for (Document obsDoc : obsDocs) {
                data.observations.add(ObservationDocument.fromDocument(obsDoc));
            }
        }
        
        return data;
    }
    
    // Convert to MongoDB Document
    public Document toDocument() {
        Document doc = new Document();
        doc.append("seriesId", seriesId);
        doc.append("title", title);
        doc.append("frequency", frequency);
        doc.append("units", units);
        doc.append("lastUpdated", lastUpdated);
        doc.append("createdAt", createdAt);
        
        if (firstDate != null) {
            doc.append("firstDate", firstDate.toString());
        }
        if (lastDate != null) {
            doc.append("lastDate", lastDate.toString());
        }
        
        List<Document> obsDocs = new ArrayList<>();
        for (ObservationDocument obs : observations) {
            obsDocs.add(obs.toDocument());
        }
        doc.append("observations", obsDocs);
        
        return doc;
    }
    
    // Getters and Setters
    public String getSeriesId() { return seriesId; }
    public void setSeriesId(String seriesId) { this.seriesId = seriesId; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    
    public String getUnits() { return units; }
    public void setUnits(String units) { this.units = units; }
    
    public String getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }
    
    public List<ObservationDocument> getObservations() { return observations; }
    public void setObservations(List<ObservationDocument> observations) { this.observations = observations; }
    
    public LocalDate getFirstDate() { return firstDate; }
    public void setFirstDate(LocalDate firstDate) { this.firstDate = firstDate; }
    
    public LocalDate getLastDate() { return lastDate; }
    public void setLastDate(LocalDate lastDate) { this.lastDate = lastDate; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public int getObservationCount() {
        return observations != null ? observations.size() : 0;
    }
    
    /**
     * Inner class for individual observations
     */
    public static class ObservationDocument {
        private String date;
        private String value;
        
        public ObservationDocument() {}
        
        public ObservationDocument(String date, String value) {
            this.date = date;
            this.value = value;
        }
        
        public static ObservationDocument fromDocument(Document doc) {
            ObservationDocument obs = new ObservationDocument();
            obs.date = doc.getString("date");
            obs.value = doc.getString("value");
            return obs;
        }
        
        public Document toDocument() {
            Document doc = new Document();
            doc.append("date", date);
            doc.append("value", value);
            return doc;
        }
        
        // Getters and Setters
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
