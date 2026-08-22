package com.github.dropguard.summer.twitter.infra;

import java.time.Duration;
import java.time.Instant;

public class HackerNewsScoring {
    private static final double GRAVITY = 1.8;

    /**
     * Calculates the ranking score based on Hacker News algorithm. score = (points - 1) / (T + 2) ^
     * gravity
     *
     * @param likeCount the number of likes
     * @param replyCount the number of replies
     * @param retweetCount the number of retweets
     * @param createdAt the time the item was created
     * @return the calculated score
     */
    public static double calculateScore(
            int likeCount, int replyCount, int retweetCount, Instant createdAt) {
        double points = likeCount + (replyCount * 2) + (retweetCount * 3);
        long hours = Math.max(0, Duration.between(createdAt, Instant.now()).toHours());
        return (points + 1) / Math.pow(hours + 2, GRAVITY);
    }
}
