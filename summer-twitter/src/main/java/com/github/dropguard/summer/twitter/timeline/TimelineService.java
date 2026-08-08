package com.github.dropguard.summer.twitter.timeline;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.redis.SummerRedisTemplate;
import com.github.dropguard.summer.twitter.infra.HackerNewsScoring;
import com.github.dropguard.summer.twitter.social.Follow;
import com.github.dropguard.summer.twitter.social.FollowRepository;
import com.github.dropguard.summer.twitter.tweet.Tweet;
import com.github.dropguard.summer.twitter.tweet.TweetRepository;
import com.github.dropguard.summer.twitter.ws.EventPublisher;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class TimelineService {

    /** Follower threshold above which an author is treated as an influencer. */
    static final int INFLUENCER_THRESHOLD = 5000;

    /** Maximum number of tweets retained in a user's timeline ZSET. */
    private static final int MAX_TIMELINE_SIZE = 1000;

    /** Maximum number of a user's own tweets retained for influencer fanout queries. */
    private static final int MAX_OWN_TWEETS = 500;

    private final SummerRedisTemplate redisTemplate;
    private final TweetRepository tweetRepository;
    private final FollowRepository followRepository;
    private final EventPublisher eventPublisher;

    public TimelineService(
            SummerRedisTemplate redisTemplate,
            TweetRepository tweetRepository,
            FollowRepository followRepository,
            EventPublisher eventPublisher) {
        this.redisTemplate = redisTemplate;
        this.tweetRepository = tweetRepository;
        this.followRepository = followRepository;
        this.eventPublisher = eventPublisher;
    }

    public void fanOut(Tweet tweet, int authorFollowerCount) {
        long timestamp = tweet.createdAt().toInstant().toEpochMilli();
        long authorId = tweet.authorId();

        if (authorFollowerCount >= INFLUENCER_THRESHOLD) {
            // Influencer: store in the author's tweet cache so getTimeline can
            // merge them at read time without fanout.
            String ownKey = "user:" + authorId + ":tweets";
            redisTemplate.getCommands().zadd(ownKey, timestamp, tweet.id().toString());
            redisTemplate.getCommands().zremrangebyrank(ownKey, 0, -(MAX_OWN_TWEETS + 1));
        } else {
            Thread.startVirtualThread(
                    () -> {
                        Long cursor = null;
                        do {
                            List<Follow> followers =
                                    followRepository.findFollowers(authorId, cursor, 1000);
                            if (followers.isEmpty()) break;

                            Map<String, Object> event =
                                    Map.of(
                                            "type", "new_tweet",
                                            "tweetId", tweet.id().toString(),
                                            "authorId", String.valueOf(authorId),
                                            "content", tweet.content());
                            for (Follow follow : followers) {
                                String tlKey = "timeline:" + follow.followerId();
                                redisTemplate
                                        .getCommands()
                                        .zadd(tlKey, timestamp, tweet.id().toString());
                                redisTemplate
                                        .getCommands()
                                        .zremrangebyrank(tlKey, 0, -(MAX_TIMELINE_SIZE + 1));
                                // Push a real-time new_tweet event to each follower who is
                                // connected on /ws/events. EventPublisher is a no-op when the
                                // follower has no open session, so offline followers are skipped.
                                eventPublisher.publish(follow.followerId(), event);
                            }

                            cursor = followers.get(followers.size() - 1).id();
                        } while (true);
                    });
        }
    }

    public List<Tweet> getTimeline(Long userId, Long cursorId, int limit) {
        List<Object> timelineIds =
                redisTemplate.getCommands().zrevrange("timeline:" + userId, 0, 200);
        Set<Long> mergedIds = new HashSet<>();

        if (timelineIds != null) {
            for (Object idObj : timelineIds) {
                if (idObj != null) {
                    mergedIds.add(Long.valueOf(idObj.toString().replace("\"", "")));
                }
            }
        }

        List<Long> influencers =
                followRepository.findInfluencerFollowing(userId, INFLUENCER_THRESHOLD);
        for (Long influencer : influencers) {
            List<Object> influencerTweets =
                    redisTemplate.getCommands().zrevrange("user:" + influencer + ":tweets", 0, 50);
            if (influencerTweets != null) {
                for (Object idObj : influencerTweets) {
                    if (idObj != null) {
                        mergedIds.add(Long.valueOf(idObj.toString().replace("\"", "")));
                    }
                }
            }
        }

        if (mergedIds.isEmpty()) {
            return List.of();
        }

        List<Tweet> tweets = tweetRepository.findByIds(new ArrayList<>(mergedIds));

        List<Map.Entry<Tweet, Double>> scoredTweets = new ArrayList<>();
        for (Tweet t : tweets) {
            double score =
                    HackerNewsScoring.calculateScore(
                            t.likeCount() != null ? t.likeCount() : 0,
                            t.replyCount() != null ? t.replyCount() : 0,
                            t.retweetCount() != null ? t.retweetCount() : 0,
                            t.createdAt().toInstant());
            scoredTweets.add(new AbstractMap.SimpleEntry<>(t, score));
        }

        scoredTweets.sort(
                (a, b) -> {
                    int cmp = Double.compare(b.getValue(), a.getValue());
                    if (cmp != 0) return cmp;
                    return b.getKey().id().compareTo(a.getKey().id());
                });

        List<Tweet> sorted =
                scoredTweets.stream().map(Map.Entry::getKey).collect(Collectors.toList());

        if (cursorId != null) {
            int idx = -1;
            for (int i = 0; i < sorted.size(); i++) {
                if (sorted.get(i).id().equals(cursorId)) {
                    idx = i;
                    break;
                }
            }
            if (idx != -1) {
                // Cursor found: return the page after it.
                sorted = sorted.subList(idx + 1, sorted.size());
            }
            // Cursor stale (the tweet it pointed at was deleted — the common case for
            // an infinite-scroll feed): leave `sorted` intact and return the head of
            // the feed rather than swallowing the whole result set.
        }

        return sorted.stream().limit(limit).collect(Collectors.toList());
    }
}
