-- Distributed token bucket, executed atomically on Redis.
--
-- KEYS[1] = the bucket's Redis key, e.g. "ratelimit:{user-1}"
-- ARGV[1] = capacity (max tokens)
-- ARGV[2] = refill rate (tokens per second)
-- ARGV[3] = current timestamp in milliseconds
--
-- Returns: 1 if allowed, 0 if rejected

local bucketKey = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillPerSecond = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- TODO 1: read existing fields
local bucket = redis.call("HMGET", bucketKey, "tokens", "last_refill")
local tokens = tonumber(bucket[1])
local lastRefill = tonumber(bucket[2])

-- TODO 2: first-time-seen key -> initialize full bucket
if tokens == nil then
    tokens = capacity
    lastRefill = now
end

-- TODO 3: lazy refill, same math as Day 2's Java version
local elapsedSeconds = math.max(0, (now - lastRefill) / 1000)
local tokensToAdd = elapsedSeconds * refillPerSecond
tokens = math.min(capacity, tokens + tokensToAdd)

-- TODO 4: check and consume
local allowed = 0
if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
end

-- TODO 5: write updated state back
redis.call("HMSET", bucketKey, "tokens", tokens, "last_refill", now)

-- TODO 6: expire idle buckets - no point keeping data around longer than
-- it takes to fully refill from empty anyway, plus a small safety buffer
redis.call("EXPIRE", bucketKey, math.ceil(capacity / refillPerSecond) + 60)

return allowed