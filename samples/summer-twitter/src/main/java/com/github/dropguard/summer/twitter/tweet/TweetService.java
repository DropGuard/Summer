package com.github.dropguard.summer.twitter.tweet;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.twitter.common.BusinessException;
import com.github.dropguard.summer.twitter.common.ResourceNotFoundException;
import com.github.dropguard.summer.twitter.common.UserNotFoundException;
import com.github.dropguard.summer.twitter.infra.SnowflakeIdGenerator;
import com.github.dropguard.summer.twitter.social.LikeRepository;
import com.github.dropguard.summer.twitter.timeline.TimelineService;
import com.github.dropguard.summer.twitter.user.User;
import com.github.dropguard.summer.twitter.user.UserRepository;
import com.github.dropguard.summer.web.HttpStatus;
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
    private final LikeRepository likeRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final TimelineService timelineService;

    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)");

    public TweetService(
            TweetRepository tweetRepository,
            UserRepository userRepository,
            LikeRepository likeRepository,
            SnowflakeIdGenerator idGenerator,
            TimelineService timelineService) {
        this.tweetRepository = tweetRepository;
        this.userRepository = userRepository;
        this.likeRepository = likeRepository;
        this.idGenerator = idGenerator;
        this.timelineService = timelineService;
    }

    public Tweet createTweet(Long authorId, String content, Long parentId) {
        Long tweetId = idGenerator.nextId();
        Tweet tweet =
                new Tweet(
                        tweetId,
                        authorId,
                        content,
                        "POST",
                        parentId,
                        0,
                        0,
                        0,
                        OffsetDateTime.now());

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
        User author =
                userRepository
                        .findById(authorId)
                        .orElseThrow(() -> new UserNotFoundException("Author not found"));
        timelineService.fanOut(tweet, author.followerCount() != null ? author.followerCount() : 0);

        return tweet;
    }

    public Tweet retweet(Long originalId, Long userId) {
        Tweet original = tweetRepository.findById(originalId);
        if (original == null) {
            throw new ResourceNotFoundException("Original tweet not found");
        }

        Long tweetId = idGenerator.nextId();
        Tweet tweet =
                new Tweet(
                        tweetId, userId, "", "RETWEET", originalId, 0, 0, 0, OffsetDateTime.now());

        tweetRepository.insert(tweet);
        tweetRepository.incrementRetweetCount(originalId);

        User author =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new UserNotFoundException("User not found"));
        timelineService.fanOut(tweet, author.followerCount() != null ? author.followerCount() : 0);

        return tweet;
    }

    public Tweet quoteTweet(Long originalId, Long userId, String content) {
        Tweet original = tweetRepository.findById(originalId);
        if (original == null) {
            throw new ResourceNotFoundException("Original tweet not found");
        }

        Long tweetId = idGenerator.nextId();
        Tweet tweet =
                new Tweet(
                        tweetId,
                        userId,
                        content,
                        "QUOTE",
                        originalId,
                        0,
                        0,
                        0,
                        OffsetDateTime.now());

        tweetRepository.insert(tweet);
        tweetRepository.incrementRetweetCount(originalId);

        User author =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new UserNotFoundException("User not found"));
        timelineService.fanOut(tweet, author.followerCount() != null ? author.followerCount() : 0);

        return tweet;
    }

    public Tweet getTweet(Long id) {
        return tweetRepository.findById(id);
    }

    public void deleteTweet(Long id, Long requesterId) {
        Tweet tweet = tweetRepository.findById(id);
        if (tweet == null) {
            throw new ResourceNotFoundException("Tweet not found");
        }
        if (!tweet.authorId().equals(requesterId)) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN.code(), "forbidden", "You can only delete your own tweets");
        }

        // Clean up likes before the tweet goes away (ON DELETE CASCADE handles
        // the FK at DB level, but we still need to decrement like counts on the
        // tweet itself — though since it's being deleted that's moot. The primary
        // benefit is that it keeps the DB and in-memory state consistent in case
        // the DB cascade fails or in a non-Postgres environment.)
        likeRepository.deleteByTweetId(id);

        // Collect and delete child tweets (replies, retweets, quotes). ON DELETE
        // CASCADE on parent_id handles this at the DB level, but we process them
        // explicitly so that each child's own likes are cleaned up and parent
        // reply counts are adjusted.
        List<Tweet> children = tweetRepository.findByParentId(id);
        for (Tweet child : children) {
            likeRepository.deleteByTweetId(child.id());
            tweetRepository.delete(child.id());
        }

        tweetRepository.delete(id);

        // If this tweet was a reply, update parent reply count
        if (tweet.parentId() != null) {
            tweetRepository.updateReplyCount(tweet.parentId(), -1);
        }
    }

    public List<Tweet> getReplies(Long parentId, Long cursor, int limit) {
        return tweetRepository.getReplies(parentId, cursor, limit);
    }
}
