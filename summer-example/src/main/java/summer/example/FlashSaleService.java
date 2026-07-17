package summer.example;

import io.lettuce.core.ScriptOutputType;
import summer.core.Component;
import summer.data.redis.SummerRedisTemplate;

@Component
public class FlashSaleService {

	private static final String STOCK_KEY_PREFIX = "seckill:item:";

	private final SummerRedisTemplate redisTemplate;

	public FlashSaleService(SummerRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	private String stockKey(String itemId) {
		return STOCK_KEY_PREFIX + itemId;
	}

	/**
	 * Initializes the stock for a given item.
	 */
	public void initStock(String itemId, int stock) {
		redisTemplate.set(stockKey(itemId), stock);
	}

	/**
	 * Attempts to purchase the item. Uses a Lua script for atomic execution in
	 * Redis.
	 *
	 * @return true if purchase successful (stock deducted), false if sold out.
	 */
	public boolean purchase(String itemId) {
		String script = "local stock = tonumber(redis.call('get', KEYS[1])) \n"
				+ "if stock == nil or stock <= 0 then \n" + "    return 0 \n" + "end \n"
				+ "redis.call('decr', KEYS[1]) \n" + "return 1";

		Long result = redisTemplate.eval(script, ScriptOutputType.INTEGER, new String[]{stockKey(itemId)});

		return result != null && result == 1L;
	}

	/**
	 * Gets current stock.
	 */
	public int getStock(String itemId) {
		Object val = redisTemplate.getRaw(stockKey(itemId));
		if (val instanceof Integer) {
			return (Integer) val;
		} else if (val != null) {
			return Integer.parseInt(val.toString());
		}
		return 0;
	}
}
