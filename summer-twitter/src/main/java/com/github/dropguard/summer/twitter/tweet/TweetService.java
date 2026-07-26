package com.github.dropguard.summer.twitter.tweet;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.twitter.common.ResourceNotFoundException;
import com.github.dropguard.summer.twitter.common.UserNotFoundException;
import com.github.dropguard.summer.twitter.infra.SnowflakeIdGenerator;
import com.github.dropguard.summer.twitter.user.User;
import com.github.dropguard.summer.twitter.user.UserRepository;

import com.github.dropguard.summer.twitter.timeline.TimelineService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TweetService {

    private final TweetRepository tweetRepository;
    private final UserRepository userRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final TimelineService timelineService;

    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)");

    public TweetService(TweetRepository tweetRepository, UserRepository userRepository, SnowflakeIdGenerator idGenerator, TimelineService timelineService) {
        this.tweetRepository = tweetRepository;
        this.userRepository = userRepository;
        this.idGenerator = idGenerator;
        this.timelineService = timelineService;
    }

    public Tweet createTweet(Long authorId, String content, Long parentId) {
        Long tweetId = idGenerator.nextId();
        Tweet tweet = new Tweet(
            tweetId,
            authorId,
            content,
            "POST",
            parentId,
            0,
            0,
            0,
            OffsetDateTime.now()
        );

        tweetRepository.insert(tweet);

        if (parentId != null) {
            tweetRepository.updateReplyCount(parentId, 1);
        }

        // Extract @mentions
        Matcher matcher = MENTION_PATTERN.matcher(content);
        List<User> mentionedUsers = new ArrayList<>();
        while (matcher.find()) {
            String username = matcher.group(1);
            Optional<User> userOpt = userRepository.findByUsername(username);
            userOpt.ifPresent(mentionedUsers::add);
        }

        // Fan the tweet out to timelines. The author's follower count decides
        // whether the follower fan-out runs inline or on a virtual thread (the
        // threshold lives inside TimelineService.fanOut). The author's own tweet
        // list is always written synchronously within fanOut.
        User author = userRepository.findById(authorId).orElseThrow(() -> new UserNotFoundException("Author not found"));
        timelineService.fanOut(tweet, author.followerCount() != null ? author.followerCount() : 0);

        return tweet;
    }

    public Tweet retweet(Long originalId, Long userId) {
        Tweet original = tweetRepository.findById(originalId);
        if (original == null) {
            throw new ResourceNotFoundException("Original tweet not found");
        }

        Long tweetId = idGenerator.nextId();
        Tweet tweet = new Tweet(
            tweetId,
            userId,
            "",
            "RETWEET",
            originalId,
            0,
            0,
            0,
            OffsetDateTime.now()
        );
        
        tweetRepository.insert(tweet);
        tweetRepository.incrementRetweetCount(originalId);
        
        User author = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
        timelineService.fanOut(tweet, author.followerCount() != null ? author.followerCount() : 0);

        return tweet;
    }

    public Tweet quoteTweet(Long originalId, Long userId, String content) {
        Tweet original = tweetRepository.findById(originalId);
        if (original == null) {
            throw new ResourceNotFoundException("Original tweet not found");
        }

        Long tweetId = idGenerator.nextId();
        Tweet tweet = new Tweet(
            tweetId,
            userId,
            content,
            "QUOTE",
            originalId,
            0,
            0,
            0,
            OffsetDateTime.now()
        );
        
        tweetRepository.insert(tweet);
        tweetRepository.incrementRetweetCount(originalId);
        
        User author = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
        timelineService.fanOut(tweet, author.followerCount() != null ? author.followerCount() : 0);

        return tweet;
    }

    public Tweet getTweet(Long id) {
        return tweetRepository.findById(id);
    }

    public void deleteTweet(Long id, Long requesterId) {
        Tweet tweet = tweetRepository.findById(id);
        if (tweet != null && tweet.authorId().equals(requesterId)) {
            tweetRepository.delete(id);
            if (tweet.parentId() != null) {
                tweetRepository.updateReplyCount(tweet.parentId(), -1);
            }
        }
    }

    public List<Tweet> getReplies(Long parentId, Long cursor, int limit) {
        return tweetRepository.getReplies(parentId, cursor, limit);
    }
}
