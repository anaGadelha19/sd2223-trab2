package sd2223.trab2.servers.rest;

import sd2223.trab2.api.Message;
import sd2223.trab2.api.java.Feeds;
import sd2223.trab2.api.rest.FeedsService;
import sd2223.trab2.servers.java.JavaFeedsPushPreconditions;
import sd2223.trab2.servers.kafka.sync.SyncPoint;

import java.util.List;

public class RestRepFeedsResource<T extends Feeds> extends RestResource implements FeedsService {

    final T impl;

    final JavaFeedsPushPreconditions preconditions;

    public RestRepFeedsResource(T impl) {
        preconditions = new JavaFeedsPushPreconditions();
        this.impl = impl;
    }


    @Override
    public long postMessage(String user, String pwd, Message msg) {
        return super.fromJavaResult(impl.postMessage(user, pwd, msg));
    }

    @Override
    public void removeFromPersonalFeed(String user, long mid, String pwd) {
        super.fromJavaResult(impl.removeFromPersonalFeed(user, mid, pwd));

    }

    @Override
    public Message getMessage(String user, long mid) {
        return super.fromJavaResult(impl.getMessage(user, mid));
    }

    @Override
    public List<Message> getMessages(String user, long time) {

        return super.fromJavaResult(impl.getMessages(user, time));
    }

    @Override
    public void subUser(String user, String userSub, String pwd) {
        super.fromJavaResult(impl.subUser(user, userSub, pwd));

    }

    @Override
    public void unsubscribeUser(String user, String userSub, String pwd) {
        super.fromJavaResult(impl.unsubscribeUser(user, userSub, pwd));

    }

    @Override
    public List<String> listSubs(String user) {
        return super.fromJavaResult(impl.listSubs(user));
    }

    @Override
    public void deleteUserFeed(String user) {
        super.fromJavaResult(impl.deleteUserFeed(user));

    }
}
