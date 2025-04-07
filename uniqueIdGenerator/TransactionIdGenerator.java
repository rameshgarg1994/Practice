import java.net.NetworkInterface;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

public final class TransactionIdGenerator {
    private String scenario;
    private static final int NODE_ID_BITS=10;
    private static final int SEQUENCE_BITS=12;
    private static final long MAX_NODE_ID = (1l<<NODE_ID_BITS)-1;
    private static final long CUSTOM_EPOCHS = ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli();
    private static final long MAX_SEQUENCE = (1l<<SEQUENCE_BITS)-1;
    private static final RandomGenerator RANDOM = RandomGenerator.getDefault();

    private final long nodId;
    private final AtomicInteger sequence = new AtomicInteger(0);
    private volatile long lastTimestamp = -1L;
    public record TransactionId(String value,Instant createdAt){}
    TransactionIdGenerator(String scenario){
        this.nodId = initializeNodeId();
        this.scenario = validateScenario(scenario);

    }
    public TransactionId generate(){
        return generate(null);
    }
    public synchronized  TransactionId generate(String uniqId){
        //get current timestamp
        long timestamp = currentTimeStamp();
        if(timestamp<lastTimestamp){
            throw new IllegalStateException("Clock is not correct");
        }
        long sequenceNum  = (timestamp==lastTimestamp)?sequence.incrementAndGet()&MAX_SEQUENCE:0l;
        if(sequenceNum==0){
            lastTimestamp = timestamp= waitForNextMilisec(timestamp);
        }
        String idValue = "%s-%d-%d-%d".formatted(scenario, timestamp, nodId, sequenceNum);
        return new TransactionId(idValue,Instant.now());
    }

    private long initializeNodeId() {
        String envId = System.getenv("NODE_ID");
        if (envId == null) {
            return generateNodeFromIp();
        } else {
            return parseNodeId(envId);
        }
    }

    private long generateNodeFromIp() {
         try{
             String ip = NetworkInterface.networkInterfaces().filter(iface -> {
                 try{
                     return iface.isUp() && iface.isLoopback();
                 }catch (Exception e){
                     return false;
                 }
             }).flatMap(NetworkInterface::inetAddresses)
                     .filter(addre -> !addre.isLoopbackAddress())
                     .findFirst()
                     .orElseThrow()
                     .getHostAddress();
             return Math.abs(ip.hashCode())%(MAX_NODE_ID+1);
         }catch(Exception e){
             return RANDOM.nextLong(0,MAX_NODE_ID+1);
         }
    }
    private long parseNodeId(String envId) {
        try{
            long id = Long.parseLong(envId);
            if(id<0||id>MAX_NODE_ID){
                throw new IllegalArgumentException(
                        "Node ID must be between 0 and {MAX_NODE_ID}");
            }
            return id;
        }catch (NumberFormatException n){
            throw new IllegalArgumentException("Invalid NODE_ID format", n);
        }
    }

    private String validateScenario(String scenario) {
        if(scenario==null || scenario.isBlank() || scenario.length() != 3){
            throw new IllegalArgumentException(
                    "Use case prefix must be 3 characters");
        }
        return scenario.toUpperCase();
    }

    private long currentTimeStamp() {
        return System.currentTimeMillis()-CUSTOM_EPOCHS;
    }
    private long waitForNextMilisec(long timestamp) {
        while((timestamp = currentTimeStamp()) <= lastTimestamp){
            Thread.onSpinWait();
        }
        sequence.set(0);
        return currentTimeStamp();
    }
}
