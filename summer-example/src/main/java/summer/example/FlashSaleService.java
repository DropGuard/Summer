package summer.example;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import summer.core.ApplicationContext;
import summer.core.Component;

@Component
public class FlashSaleService {

	private final ApplicationContext context;
	private RedisCommands<String, Object> redisCommands;

	public FlashSaleService(ApplicationContext context) {
		this.context = context;
	}

	private RedisCommands<String, Object> getRedisCommands() {
		if (redisCommands == null) {
			// Unchecked cast to workaround generic type erasure on getBean
			this.redisCommands = (RedisCommands<String, Object>) context.getBean(RedisCommands.class);
		}
		return redisCommands;
	}

	/**
	 * Initializes the stock for a given item.
	 */
	public void initStock(String itemId, int stock) {
		// Jackson will serialize the int natively.
		getRedisCommands().set("seckill:item:" + itemId, stock);
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

		Long result = getRedisCommands().eval(script, ScriptOutputType.INTEGER, new String[]{"seckill:item:" + itemId});

		return result != null && result == 1L;
	}

	/**
	 * Gets current stock.
	 */
	public int getStock(String itemId) {
		Object val = getRedisCommands().get("seckill:item:" + itemId);
		if (val instanceof Integer) {
			return (Integer) val;
		} else if (val != null) {
			return Integer.parseInt(val.toString());
		}
		return 0;
	}
}
