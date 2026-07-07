package summer.twitter.timeline;

import summer.core.Component;
import summer.data.redis.SummerRedisTemplate;
import summer.twitter.tweet.Tweet;
import summer.twitter.tweet.TweetRepository;
import summer.twitter.social.FollowRepository;
import summer.twitter.social.Follow;
import summer.twitter.infra.HackerNewsScoring;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class TimelineService {

    private final SummerRedisTemplate redisTemplate;
    private final TweetRepository tweetRepository;
    private final FollowRepository followRepository;

    public TimelineService(SummerRedisTemplate redisTemplate, TweetRepository tweetRepository, FollowRepository followRepository) {
        this.redisTemplate = redisTemplate;
        this.tweetRepository = tweetRepository;
        this.followRepository = followRepository;
    }

    public void fanOut(Tweet tweet, int authorFollowerCount) {
        long timestamp = tweet.createdAt().toInstant().toEpochMilli();
        long authorId = tweet.authorId();
        
        // Always add to the author's own tweet list
        redisTemplate.getCommands().zadd("user:" + authorId + ":tweets", timestamp, tweet.id().toString());

        if (authorFollowerCount < 5000) {
            Thread.startVirtualThread(() -> {
                Long cursor = null;
                do {
                    List<Follow> followers = followRepository.findFollowers(authorId, cursor, 1000);
                    if (followers.isEmpty()) break;
                    
                    for (Follow follow : followers) {
                        redisTemplate.getCommands().zadd("timeline:" + follow.followerId(), timestamp, tweet.id().toString());
                    }
                    
                    cursor = followers.get(followers.size() - 1).id();
                } while (true);
            });
        }
    }

    public List<Tweet> getTimeline(Long userId, Long cursorId, int limit) {
        List<Object> timelineIds = redisTemplate.getCommands().zrevrange("timeline:" + userId, 0, 200);
        Set<Long> mergedIds = new HashSet<>();
        
        if (timelineIds != null) {
            for (Object idObj : timelineIds) {
                if (idObj != null) {
                    mergedIds.add(Long.valueOf(idObj.toString().replace("\"", "")));
                }
            }
        }
        
        List<Long> bigVs = followRepository.findBigVFollowing(userId, 5000);
        for (Long bigV : bigVs) {
            List<Object> bigVTweets = redisTemplate.getCommands().zrevrange("user:" + bigV + ":tweets", 0, 50);
            if (bigVTweets != null) {
                for (Object idObj : bigVTweets) {
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
            double score = HackerNewsScoring.calculateScore(
                t.likeCount() != null ? t.likeCount() : 0, 
                t.replyCount() != null ? t.replyCount() : 0, 
                t.retweetCount() != null ? t.retweetCount() : 0,
                t.createdAt().toInstant()
            );
            scoredTweets.add(new AbstractMap.SimpleEntry<>(t, score));
        }
        
        scoredTweets.sort((a, b) -> {
            int cmp = Double.compare(b.getValue(), a.getValue());
            if (cmp != 0) return cmp;
            return b.getKey().id().compareTo(a.getKey().id());
        });
        
        List<Tweet> sorted = scoredTweets.stream().map(Map.Entry::getKey).collect(Collectors.toList());
        
        if (cursorId != null) {
            int idx = -1;
            for (int i = 0; i < sorted.size(); i++) {
                if (sorted.get(i).id().equals(cursorId)) {
                    idx = i;
                    break;
                }
            }
            if (idx != -1) {
                sorted = sorted.subList(idx + 1, sorted.size());
            } else {
                sorted = List.of();
            }
        }
        
        return sorted.stream().limit(limit).collect(Collectors.toList());
    }
}
