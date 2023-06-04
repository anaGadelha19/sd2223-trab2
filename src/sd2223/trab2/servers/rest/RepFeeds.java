package sd2223.trab2.servers.rest;

import com.google.gson.Gson;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import sd2223.trab2.api.Message;
import sd2223.trab2.api.java.Feeds;
import sd2223.trab2.api.java.Result;
import sd2223.trab2.servers.Domain;
import sd2223.trab2.servers.kafka.KafkaPublisher;
import sd2223.trab2.servers.kafka.KafkaSubscriber;
import sd2223.trab2.servers.kafka.RecordProcessor;
import sd2223.trab2.servers.kafka.sync.SyncPoint;
import utils.JSON;

import static sd2223.trab2.api.java.Result.ErrorCode.*;
import static sd2223.trab2.api.java.Result.error;
import static sd2223.trab2.api.java.Result.ok;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RepFeeds<T extends Feeds> implements Feeds, RecordProcessor {

    private static final long FEEDS_MID_PREFIX = 1_000_000_000;

    private static final String FEEDS_TOPIC = "feedsTopic";
    private static final String POST = "post";

    private static final String REMOVE_MSG = "removeMsg";

    private static final String SUB = "sub";
    private static final String UNSUB = "unsub";

    private KafkaPublisher publisher;
    private KafkaSubscriber subscriber;

    private SyncPoint sync;

    final String KAFKA_BROKERS = "kafka:9092";

    private Gson json;


    protected AtomicLong serial = new AtomicLong(Domain.uuid() * FEEDS_MID_PREFIX);

    final protected T preconditions;


    public RepFeeds(T preconditions, SyncPoint sync) {
        json = new Gson();
        this.sync = sync;
        this.preconditions = preconditions;
        publisher = KafkaPublisher.createPublisher(KAFKA_BROKERS);
        subscriber = KafkaSubscriber.createSubscriber(KAFKA_BROKERS, List.of("kafkadirectory"), "earliest");
        subscriber.start(false, (r) -> onReceive(r));
    }

    @Override
    public void onReceive(ConsumerRecord<String, String> r) {

        var key = r.key();
        switch (key) {
            case POST:
                receivePostMsg(r.value(), r.offset());
                break;
            case SUB:
                receiveSubscribe(r.value(), r.offset());
                break;
            case UNSUB:
                receiveUnsubscribe(r.value(), r.offset());
                break;
            case REMOVE_MSG:
                receiveRemoveMessage(r.value(), r.offset());

        }
    }


    static protected record FeedInfo(String user, Set<Long> messages, Set<String> following, Set<String> followers) {
        public FeedInfo(String user) {
            this(user, new HashSet<>(), new HashSet<>(), ConcurrentHashMap.newKeySet());
        }
    }


    protected Map<Long, Message> messages = new ConcurrentHashMap<>();
    protected Map<String, FeedInfo> feeds = new ConcurrentHashMap<>();

    @Override
    public Result<Long> postMessage(String user, String pwd, Message msg) {

        var preconditionsResult = preconditions.postMessage(user, pwd, msg);
        if (!preconditionsResult.isOK())
            return preconditionsResult;

        Long mid = serial.incrementAndGet();
        msg.setId(mid);
        msg.setCreationTime(System.currentTimeMillis());

        long offset = publisher.publish(FEEDS_TOPIC, POST, JSON.encode(msg));
        if (offset < 0) {
            return error(INTERNAL_ERROR);
        }
        sync.waitForResult(offset);
        return Result.ok(mid);
    }

    @Override
    public Result<Void> removeFromPersonalFeed(String user, long mid, String pwd) {
        var preconditionsResult = preconditions.removeFromPersonalFeed(user, mid, pwd);
        if (!preconditionsResult.isOK())
            return preconditionsResult;

        var ufi = feeds.get(user);
        if (ufi == null)
            return error(NOT_FOUND);

        List<String> userMid = new ArrayList<>();
        userMid.add(user);
        userMid.add(Long.toString(mid));
        long offset = publisher.publish(FEEDS_TOPIC, REMOVE_MSG, JSON.encode(userMid));
        if (offset < 0) {
            return error(INTERNAL_ERROR);
        }
        //deleteFromUserFeed(user, Set.of(mid));

        return ok();
    }

    @Override
    public Result<Message> getMessage(String user, long mid) {
        return null;
    }

    @Override
    public Result<List<Message>> getMessages(String user, long time) {
        return null;
    }

    @Override
    public Result<Void> subUser(String user, String userSub, String pwd) {
        var preconditionsResult = preconditions.subUser(user, userSub, pwd);
        if (!preconditionsResult.isOK())
            return preconditionsResult;

        List<String> user1User2 = new ArrayList<>();
        user1User2.add(user);
        user1User2.add(userSub);
        long offset = publisher.publish(FEEDS_TOPIC, SUB, JSON.encode(user1User2));
        if (offset < 0) {
            return error(INTERNAL_ERROR);
        }
        sync.waitForResult(offset);
        return ok();
    }

    @Override
    public Result<Void> unsubscribeUser(String user, String userSub, String pwd) {
        var preconditionsResult = preconditions.subUser(user, userSub, pwd);
        if (!preconditionsResult.isOK())
            return preconditionsResult;

        List<String> user1User2 = new ArrayList<>();
        user1User2.add(user);
        user1User2.add(userSub);
        long offset = publisher.publish(FEEDS_TOPIC, UNSUB, JSON.encode(user1User2));
        if (offset < 0) {
            return error(INTERNAL_ERROR);
        }
        sync.waitForResult(offset);
        return ok();
    }

    @Override
    public Result<List<String>> listSubs(String user) {
        return null;
    }

    @Override
    public Result<Void> deleteUserFeed(String user) {
        return null;
    }

    private void receivePostMsg(String value, long offset) {
        Message message = JSON.decode(value, Message.class);

        FeedInfo ufi = feeds.computeIfAbsent(message.getUser(), FeedInfo::new);
        synchronized (ufi.user()) {
            ufi.messages().add(message.getId());
            messages.putIfAbsent(message.getId(), message);
        }
        sync.setResult(offset, Result.ok(message.getId()));
    }

    private void receiveSubscribe(String value, long offset) {
        List<String> users = JSON.decode(value, ArrayList.class);

        var ufi = feeds.computeIfAbsent(users.get(0), FeedInfo::new);
        synchronized (ufi.user()) {
            ufi.following().add(users.get(1));
        }


        sync.setResult(offset, Result.ok());


    }

    private void receiveUnsubscribe(String value, long offset) {
        List<String> users = JSON.decode(value, ArrayList.class);
        FeedInfo ufi = feeds.computeIfAbsent(users.get(0), FeedInfo::new);
        synchronized (ufi.user()) {
            ufi.following().remove(users.get(1));
        }
        sync.setResult(offset, Result.ok());

    }

    private void receiveRemoveMessage(String value, long offset) {
        List<String> auxL = JSON.decode(value, ArrayList.class);

        var ufi = feeds.get(auxL.get(0));
        long mid = Long.parseLong(auxL.get(1));

        synchronized (ufi.user()) {
            ufi.messages().remove(mid);
        }
        sync.setResult(offset, Result.ok());
    }


}
