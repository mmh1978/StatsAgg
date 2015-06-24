package com.pearson.statsagg.metric_formats.opentsdb;

import com.pearson.statsagg.metric_formats.GenericMetricFormat;
import com.pearson.statsagg.metric_formats.graphite.GraphiteMetricFormat;
import com.pearson.statsagg.metric_formats.influxdb.InfluxdbMetricFormat_v1;
import com.pearson.statsagg.utilities.JsonUtils;
import com.pearson.statsagg.utilities.StackTrace;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringEscapeUtils;
import org.boon.Boon;
import org.boon.core.value.LazyValueMap;
import org.boon.core.value.ValueList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class OpenTsdbMetric implements GraphiteMetricFormat, OpenTsdbMetricFormat, GenericMetricFormat, InfluxdbMetricFormat_v1 {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenTsdbMetric.class.getName());
    
    private long hashKey_ = -1;
    
    private final long metricTimestamp_;
    private final BigDecimal metricValue_;
    private final boolean isTimestampInMilliseconds_;
    private long metricReceivedTimestampInMilliseconds_ = -1;
    
    private String metricKey_ = null;
    private final int metricLength_;  // 'metric' refers to the OpenTSDB 'metric name' 

    public OpenTsdbMetric(String metric, long metricTimestamp, BigDecimal metricValue, List<OpenTsdbTag> tags, 
            boolean isTimestampInMilliseconds, long metricReceivedTimestampInMilliseconds) {
        this.metricTimestamp_ = metricTimestamp;
        this.metricValue_ = metricValue;
        this.isTimestampInMilliseconds_ = isTimestampInMilliseconds;
        this.metricReceivedTimestampInMilliseconds_ = metricReceivedTimestampInMilliseconds;
        
        this.metricKey_ = createAndGetMetricKey(metric, tags);
        
        if (metric != null) this.metricLength_ = metric.length();
        else this.metricLength_ = -1;
    }

    public OpenTsdbMetric(long metricTimestamp, BigDecimal metricValue, boolean isTimestampInMilliseconds, long metricReceivedTimestampInMilliseconds, 
            String metricKey, int metricLength) {
        this.metricTimestamp_ = metricTimestamp;
        this.metricValue_ = metricValue;
        this.isTimestampInMilliseconds_ = isTimestampInMilliseconds;
        this.metricReceivedTimestampInMilliseconds_ = metricReceivedTimestampInMilliseconds;
        
        this.metricKey_ = metricKey;
        this.metricLength_ = metricLength;
    }    
    
    public final String createAndGetMetricKey(String metric, List<OpenTsdbTag> tags) {

        if (metricKey_ != null) return metricKey_;
        if (metric == null) return null;
        
        ArrayList sortedUnparseTags = getSortedUnparsedTags(tags);
        if ((sortedUnparseTags == null) || sortedUnparseTags.isEmpty()) return null;
        
        StringBuilder metricKey = new StringBuilder(64);
        
        metricKey.append(metric);
        metricKey.append(" : ");

        for (int i = 0; i < sortedUnparseTags.size(); i++) {
            metricKey.append(sortedUnparseTags.get(i));
            if ((i + 1) != sortedUnparseTags.size()) metricKey.append(" ");
        }
        
        metricKey_ = metricKey.toString();
        
        return metricKey_;
    }
    
    private String getMetricFromMetricKey() {
        if (metricKey_ == null) return null;
        if (metricLength_ < 0) return null;
        if (metricLength_ >= metricKey_.length()) return metricKey_;
            
        return metricKey_.substring(0, metricLength_);
    }

    private List<OpenTsdbTag> getMetricTagsFromMetricKey() {
        if (metricKey_ == null) return new ArrayList<>();
        if (metricLength_ < 1) return new ArrayList<>();
        if ((metricLength_ + 3) >= metricKey_.length()) return new ArrayList<>();
        
        List<OpenTsdbTag> openTsdbTags = OpenTsdbTag.parseTags(metricKey_, metricLength_ + 2);
        return openTsdbTags;
    }
    
    public static String getOpenTsdbFormattedMetric(String input) {

        if (input == null) {
            return null;
        }

        StringBuilder openTsdbFormatted = new StringBuilder();
 
        for (char character : input.toCharArray()) {
            
            if ((character >= 'a') && (character <= 'z')) {
                openTsdbFormatted.append(character);
                continue;
            }
            
            if ((character >= 'A') && (character <= 'Z')) {
                openTsdbFormatted.append(character);
                continue;
            }
            
            if ((character == '-') || (character == '_') || (character == '.') || (character == ',') || (character == '/')) {
                openTsdbFormatted.append(character);
                continue;
            }

        }

        return openTsdbFormatted.toString();
    }

    @Override
    public String toString() {        
        return getOpenTsdbTelnetFormatString() + " @ " + metricReceivedTimestampInMilliseconds_;
    }

    @Override
    public String getGraphiteFormatString() {
        StringBuilder stringBuilder = new StringBuilder();
        
        stringBuilder.append(getMetric()).append(" ").append(getMetricValueString()).append(" ").append(getMetricTimestampInSeconds());
        
        return stringBuilder.toString();
    }

    @Override
    public String getOpenTsdbTelnetFormatString() {
        return getOpenTsdbTelnetFormatString(false);
    }
    
    @Override
    public String getOpenTsdbTelnetFormatString(boolean sanitizeMetrics) {
        StringBuilder stringBuilder = new StringBuilder();
        
        String metric = sanitizeMetrics ? getOpenTsdbFormattedMetric(getMetric()) : getMetric();
        
        stringBuilder.append(metric).append(" ").append(metricTimestamp_).append(" ").append(getMetricValueString()).append(" ");

        List<OpenTsdbTag> openTsdbTags = getMetricTagsFromMetricKey();
        
        if (openTsdbTags != null) {
            for (int i = 0; i < openTsdbTags.size(); i++) {
                String tag = sanitizeMetrics ? getOpenTsdbFormattedMetric(openTsdbTags.get(i).getTag()) : openTsdbTags.get(i).getTag();
                stringBuilder.append(tag);
                if ((i + 1) != openTsdbTags.size()) stringBuilder.append(" ");
            }
        }
        
        return stringBuilder.toString();
    }
    
    @Override
    public String getOpenTsdbJsonFormatString() {
        return getOpenTsdbJsonFormatString(false);
    }
    
    @Override
    public String getOpenTsdbJsonFormatString(boolean sanitizeMetrics) {
                
        String metric = sanitizeMetrics ? getOpenTsdbFormattedMetric(getMetric()) : getMetric();
        List<OpenTsdbTag> openTsdbTags = getMetricTagsFromMetricKey();

        if ((metric == null) || metric.isEmpty()) return null;
        if (metricTimestamp_ < 0) return null;
        if ((getMetricValue() == null)) return null;
        
        StringBuilder openTsdbJson = new StringBuilder();

        openTsdbJson.append("{");

        openTsdbJson.append("\"metric\":\"").append(StringEscapeUtils.escapeJson(metric)).append("\",");
        openTsdbJson.append("\"timestamp\":").append(metricTimestamp_).append(",");
        openTsdbJson.append("\"value\":").append(getMetricValueString()).append(",");

        openTsdbJson.append("\"tags\":{");

        if (openTsdbTags != null) {
            for (int j = 0; j < openTsdbTags.size(); j++) {
                OpenTsdbTag tag = openTsdbTags.get(j);
                
                if (sanitizeMetrics) {
                    openTsdbJson.append("\"").append(StringEscapeUtils.escapeJson(getOpenTsdbFormattedMetric(tag.getTagKey())));
                    openTsdbJson.append("\":\"").append(StringEscapeUtils.escapeJson(getOpenTsdbFormattedMetric(tag.getTagValue()))).append("\"");
                }
                else {
                    openTsdbJson.append("\"").append(StringEscapeUtils.escapeJson(tag.getTagKey()));
                    openTsdbJson.append("\":\"").append(StringEscapeUtils.escapeJson(tag.getTagValue())).append("\"");
                }
                
                if ((j + 1) != openTsdbTags.size()) openTsdbJson.append(",");
            }
        }
        
        openTsdbJson.append("}");

        openTsdbJson.append("}");
        
        return openTsdbJson.toString();
    }
    
    @Override
    public String getInfluxdbV1JsonFormatString() {

        String metric = getMetric();
        List<OpenTsdbTag> openTsdbTags = getMetricTagsFromMetricKey();

        if ((metric == null) || metric.isEmpty()) return null;
        if (metricTimestamp_ < 0) return null;
        if ((getMetricValue() == null)) return null;

        StringBuilder influxdbJson = new StringBuilder();

        influxdbJson.append("{");

        // the metric name, with the prefix already built-in
        influxdbJson.append("\"name\":\"");
        influxdbJson.append(StringEscapeUtils.escapeJson(metric)).append("\",");

        // column order: value, time, tag(s)
        influxdbJson.append("\"columns\":[\"value\",\"time\"");

        if (openTsdbTags != null && !openTsdbTags.isEmpty()) {
            influxdbJson.append(",");
            
            for (int j = 0; j < openTsdbTags.size(); j++) {
                OpenTsdbTag tag = openTsdbTags.get(j);
                influxdbJson.append("\"").append(StringEscapeUtils.escapeJson(tag.getTagKey())).append("\"");
                if ((j + 1) != openTsdbTags.size()) influxdbJson.append(",");
            }
        }

        influxdbJson.append("],");
        
        // only include one point in the points array. note-- timestamp will always be sent to influxdb in milliseconds
        influxdbJson.append("\"points\":[[");
        influxdbJson.append(getMetricValueString()).append(",");
        influxdbJson.append(getMetricTimestampInMilliseconds());
        
        if (openTsdbTags != null && !openTsdbTags.isEmpty()) {
            influxdbJson.append(",");
            
            for (int j = 0; j < openTsdbTags.size(); j++) {
                OpenTsdbTag tag = openTsdbTags.get(j);
                influxdbJson.append("\"").append(StringEscapeUtils.escapeJson(tag.getTagValue())).append("\"");
                if ((j + 1) != openTsdbTags.size()) influxdbJson.append(",");
            }
        }
                    
        influxdbJson.append("]]}");

        return influxdbJson.toString();
    }
    
    public static String getOpenTsdbJson(List<? extends OpenTsdbMetricFormat> openTsdbFormatMetrics, boolean sanitizeMetrics) {
        
        if (openTsdbFormatMetrics == null) return null;

        StringBuilder openTsdbJson = new StringBuilder();
        
        openTsdbJson.append("[");
        
        for (int i = 0; i < openTsdbFormatMetrics.size(); i++) {
            OpenTsdbMetricFormat openTsdbMetric = openTsdbFormatMetrics.get(i);
            String openTsdbJsonString = openTsdbMetric.getOpenTsdbJsonFormatString(sanitizeMetrics);
            
            if (openTsdbJsonString != null) {
                openTsdbJson.append(openTsdbJsonString);
                if ((i + 1) != openTsdbFormatMetrics.size()) openTsdbJson.append(",");
            }
        }
        
        openTsdbJson.append("]");
        
        return openTsdbJson.toString();
    }
    
    public static OpenTsdbMetric parseOpenTsdbMetric(String unparsedMetric, String metricPrefix, long metricReceivedTimestampInMilliseconds) {
        
        if (unparsedMetric == null) {
            return null;
        }
        
        try {
            int metricIndexRange = unparsedMetric.indexOf(' ', 0);
            String metric = null;
            if (metricIndexRange > 0) {
                if ((metricPrefix != null) && !metricPrefix.isEmpty()) metric = metricPrefix + unparsedMetric.substring(0, metricIndexRange);
                else metric = unparsedMetric.substring(0, metricIndexRange);
            }

            int metricTimestampIndexRange = unparsedMetric.indexOf(' ', metricIndexRange + 1);
            String metricTimestampString = null;
            long metricTimestamp = -1;
            Boolean isTimestampInMilliseconds = null;
            if (metricTimestampIndexRange > 0) {
                metricTimestampString = unparsedMetric.substring(metricIndexRange + 1, metricTimestampIndexRange);
                metricTimestamp = Long.parseLong(metricTimestampString);
                if (metricTimestampString.length() == 13) isTimestampInMilliseconds = true;
                else if (metricTimestampString.length() == 10) isTimestampInMilliseconds = false;
            }

            int metricValueIndexRange = unparsedMetric.indexOf(' ', metricTimestampIndexRange + 1);
            
            BigDecimal metricValueBigDecimal = null;
            if (metricValueIndexRange > 0) {
                String metricValue = unparsedMetric.substring(metricTimestampIndexRange + 1, metricValueIndexRange);
                metricValueBigDecimal = new BigDecimal(metricValue);
            }
            
            List<OpenTsdbTag> openTsdbTags = OpenTsdbTag.parseTags(unparsedMetric, metricValueIndexRange);
            
            if ((metric == null) || metric.isEmpty() || 
                    (metricValueBigDecimal == null) ||
                    (metricTimestampString == null) || (metricTimestamp == -1) || 
                    (openTsdbTags == null) || (openTsdbTags.isEmpty()) || 
                    (isTimestampInMilliseconds == null) || ((metricTimestampString.length() != 10) && (metricTimestampString.length() != 13))) {
                logger.warn("Metric parse error: \"" + unparsedMetric + "\"");
                return null;
            }
            else {
                OpenTsdbMetric openTsdbMetric = new OpenTsdbMetric(metric, metricTimestamp, metricValueBigDecimal, openTsdbTags, 
                        isTimestampInMilliseconds, metricReceivedTimestampInMilliseconds); 
                
                if ((openTsdbMetric.getMetricKey() != null) && (openTsdbMetric.getMetricTimestampInMilliseconds() > -1)) return openTsdbMetric;
                else return null;
            }
        }
        catch (Exception e) {
            logger.error("Error on " + unparsedMetric + System.lineSeparator() + e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));  
            return null;
        }
    }
    
    public static ArrayList getSortedUnparsedTags(List<OpenTsdbTag> openTsdbTags) {
        
        ArrayList<String> sortedUnparsedTags = new ArrayList<>();
        
        if ((openTsdbTags == null) || openTsdbTags.isEmpty()) {
            sortedUnparsedTags.trimToSize();
            return sortedUnparsedTags;
        }
                
        for (OpenTsdbTag openTsdbTag : openTsdbTags) {
            String tag = openTsdbTag.getTag();
            if (tag != null) sortedUnparsedTags.add(tag);
        }
        
        Collections.sort(sortedUnparsedTags);
        sortedUnparsedTags.trimToSize();
        
        return sortedUnparsedTags;
    }
    
    public static List<OpenTsdbMetric> parseOpenTsdbJson(String inputJson, String metricPrefix, long metricsReceivedTimestampInMilliseconds) {
        return parseOpenTsdbJson(inputJson, metricPrefix, metricsReceivedTimestampInMilliseconds, new ArrayList<Integer>());
    }
    
    /* successCountAndFailCount is modified by this method. index-0 will have the successfully parsed metric count, and index-1 will have the metrics with errors count */
    public static List<OpenTsdbMetric> parseOpenTsdbJson(String inputJson, String metricPrefix, long metricsReceivedTimestampInMilliseconds, List<Integer> successCountAndFailCount) {

        if ((inputJson == null) || inputJson.isEmpty()) {
            return new ArrayList<>();
        }

        int successMetricCount = 0, errorMetricCount = 0, totalMetricCount = 0;
        
        ValueList parsedJsonObject = null;
        
        try {
            parsedJsonObject = (ValueList) Boon.fromJson(inputJson);
        }
        catch (Exception e) {
            logger.warn(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
            
        if (parsedJsonObject == null) return new ArrayList<>();
            
        StringBuilder openTsdbMetricsString = new StringBuilder();
                
        for (Object openTsdbMetricJsonObject : parsedJsonObject) {
            try {
                LazyValueMap openTsdbMetric = (LazyValueMap) openTsdbMetricJsonObject;
                
                String metric = (String) openTsdbMetric.get("metric");
                
                Object timestampObject = openTsdbMetric.get("timestamp");
                String timestampString = JsonUtils.convertNumericObjectToString(timestampObject, false);
                
                Object valueObject = openTsdbMetric.get("value");
                String valueString = JsonUtils.convertNumericObjectToString(valueObject, true);
                
                LazyValueMap tagsObject = (LazyValueMap) openTsdbMetric.get("tags");
                StringBuilder tagsString = new StringBuilder();
                int tagCounter = 0;
                for (String tagKey : tagsObject.keySet()) {
                    tagsString.append(tagKey).append("=").append(tagsObject.get(tagKey));
                    if ((tagCounter + 1) < tagsObject.size()) tagsString.append(" ");
                }
                
                String openTsdbMetricString = metric + " " + timestampString + " " + valueString + " " + tagsString.toString() + "\n";
                openTsdbMetricsString.append(openTsdbMetricString);
                
                totalMetricCount++;
            }
            catch (Exception e) {
                totalMetricCount++;                
                logger.warn(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }

        List<OpenTsdbMetric> openTsdbMetrics = OpenTsdbMetric.parseOpenTsdbMetrics(openTsdbMetricsString.toString(), 
                metricPrefix, metricsReceivedTimestampInMilliseconds);
        
        successMetricCount = openTsdbMetrics.size();
        errorMetricCount = totalMetricCount - successMetricCount;
        if (successCountAndFailCount == null) successCountAndFailCount = new ArrayList<>();
        if (!successCountAndFailCount.isEmpty()) successCountAndFailCount.clear();
        successCountAndFailCount.add(successMetricCount);
        successCountAndFailCount.add(errorMetricCount);

        return openTsdbMetrics;
    }
    
    public static List<OpenTsdbMetric> parseOpenTsdbMetrics(String unparsedMetrics, String metricPrefix, long metricReceivedTimestampInMilliseconds) {
        
        if ((unparsedMetrics == null) || unparsedMetrics.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<OpenTsdbMetric> openTsdbMetrics = new ArrayList();
            
        try {
            int currentIndex = 0;
            int newLineLocation = 0;

            while(newLineLocation != -1) {
                newLineLocation = unparsedMetrics.indexOf('\n', currentIndex);

                String unparsedMetric;

                if (newLineLocation == -1) {
                    unparsedMetric = unparsedMetrics.substring(currentIndex, unparsedMetrics.length());
                }
                else {
                    unparsedMetric = unparsedMetrics.substring(currentIndex, newLineLocation);
                    currentIndex = newLineLocation + 1;
                }

                if ((unparsedMetric != null) && !unparsedMetric.isEmpty()) {
                    OpenTsdbMetric openTsdbMetric = OpenTsdbMetric.parseOpenTsdbMetric(unparsedMetric.trim(), metricPrefix, metricReceivedTimestampInMilliseconds);
                    if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                }
            }
        }
        catch (Exception e) {
            logger.warn(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return openTsdbMetrics;
    }
    
    /*
    For every unique metric key, get the OpenTsdbMetric with the most recent 'metric timestamp'. 
    
    In the event that multiple OpenTsdbMetrics share the same 'metric key' and 'metric timestamp', 
    then 'metric received timestamp' is used as a tiebreaker. 
    
    In the event that multiple OpenTsdbMetrics also share the same 'metric received timestamp', 
    then this method will return the first OpenTsdbMetric that it scanned that met these criteria
    */
    public static Map<String,OpenTsdbMetric> getMostRecentOpenTsdbMetricByMetricKey(List<OpenTsdbMetric> openTsdbMetrics) {
        
        if (openTsdbMetrics == null || openTsdbMetrics.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, OpenTsdbMetric> mostRecentOpenTsdbMetricsByMetricKey = new HashMap<>();

        for (OpenTsdbMetric openTsdbMetric : openTsdbMetrics) {
            try {
                boolean doesAlreadyContainMetricKey = mostRecentOpenTsdbMetricsByMetricKey.containsKey(openTsdbMetric.getMetricKey());

                if (doesAlreadyContainMetricKey) {
                    OpenTsdbMetric currentMostRecentOpenTsdbMetric = mostRecentOpenTsdbMetricsByMetricKey.get(openTsdbMetric.getMetricKey());

                    if (openTsdbMetric.getMetricTimestampInMilliseconds() > currentMostRecentOpenTsdbMetric.getMetricTimestampInMilliseconds()) {
                        mostRecentOpenTsdbMetricsByMetricKey.put(openTsdbMetric.getMetricKey(), openTsdbMetric);
                    }
                    else if (openTsdbMetric.getMetricTimestampInMilliseconds() == currentMostRecentOpenTsdbMetric.getMetricTimestampInMilliseconds()) {
                        if (openTsdbMetric.getMetricReceivedTimestampInMilliseconds() > currentMostRecentOpenTsdbMetric.getMetricReceivedTimestampInMilliseconds()) {
                            mostRecentOpenTsdbMetricsByMetricKey.put(openTsdbMetric.getMetricKey(), openTsdbMetric);
                        }
                    }
                }
                else {
                    mostRecentOpenTsdbMetricsByMetricKey.put(openTsdbMetric.getMetricKey(), openTsdbMetric);
                }
            }
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }

        return mostRecentOpenTsdbMetricsByMetricKey;
    }

    public long getHashKey() {
        return this.hashKey_;
    }
    
    @Override
    public long getMetricHashKey() {
        return getHashKey();
    }
    
    public void setHashKey(long hashKey) {
        this.hashKey_ = hashKey;
    }
    
    @Override
    public String getMetricKey() {
        return metricKey_;
    }
    
    public String getMetric() {
        return getMetricFromMetricKey();
    }
    
    public int getMetricLength() {
        return metricLength_;
    }
    
    public long getMetricTimestamp() {
        return metricTimestamp_;
    }
    
    @Override
    public int getMetricTimestampInSeconds() {
        if (!isTimestampInMilliseconds_) return (int) metricTimestamp_;
        else return (int) (metricTimestamp_ / 1000);
    }
    
    @Override
    public long getMetricTimestampInMilliseconds() {
        if (isTimestampInMilliseconds_) return metricTimestamp_;
        else return (metricTimestamp_ * 1000);
    }
    
    public BigDecimal getMetricValue() {
        return metricValue_;
    }
    
    @Override
    public String getMetricValueString() {
        if (metricValue_ == null) return null;
        return metricValue_.stripTrailingZeros().toPlainString();
    }
    
    @Override
    public BigDecimal getMetricValueBigDecimal() {
        return metricValue_;
    }

    public List<OpenTsdbTag> getTags() {
        return getMetricTagsFromMetricKey();
    }
    
    public boolean isTimestampInMilliseconds() {
        return isTimestampInMilliseconds_;
    }
    
    @Override
    public long getMetricReceivedTimestampInMilliseconds() {
        return metricReceivedTimestampInMilliseconds_;
    }

}
