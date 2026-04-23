package edu.yu.mdm;

/** Defines the API for the RedisLookup assignment: see the requirements
 * document for more information.
 *
 * Please note the public constants defined below (and follow the implied
 * semantics).
 *
 * Students MAY NOT add to the constructor IMPLEMENTATION.  Students MAY NOT
 * add additional constructor signatures.
 *
 * @author Avraham Leff
 */

import java.io.File;
import java.util.*;
import redis.clients.jedis.Jedis;

public abstract class RedisLookupBase {

  /** The index of the Redis database to which ALL client requests must be
   * addressed, and to which all data MUST be stored.
   */
  public static final int DB_INDEX = 15;


  /** Constructor is supplied with a Jedis instance (that has been created with
   * the no-arg constructor).  The constructor is also supplied with two File
   * instances: the first corresponds to the "blocks" data-set, the second
   * corresponds to the "locations" data-set (see requirements doc for
   * details).
   *
   * The implementation must process the specified files so as to to store
   * Redis data-structures in the named database.  Other RedisLookup instances
   * will rely on these Redis data-structures to fulfill the API contract
   * below.
   *
   * @param conn a valid connection to Redis: The implemention MUST select DB
   * number DB_INDEX as its  working database.
   * @param blocks blocks file
   * @param locations locations file
   */
  public RedisLookupBase(final Jedis conn, final File blocks, final File locations) {
    this(conn);
  }

  /** Constructor is only supplied with a Jedis instance (that has been created
   * with the no-arg constructor).  Implementation instances constructed with
   * this constructor can only access state placed in Redis by an instance
   * created by the alternative constructor.
   *
   * @param conn a valid connection to Redis: The implemention MUST select DB
   * number DB_INDEX as its  working database.
   */
  public RedisLookupBase(final Jedis conn) {
    this.conn = conn;
    conn.select(DB_INDEX);
  }

  /** Given an ip address, attempts to map to location information.
   *
   * @param ipAddress IPv4 address represented in dotted-decimal notation as
   * four sets of numbers (0-255) separated by periods, such as 192.168.1.1.
   * @return null if mapping couldn't be performed (e.g., if the ip address or
   * location information not present in the supplied data-sets), otherwise an
   * array of size three: the first element corresponds to a "country name",
   * the second element corresponds to the "time zone", the third element
   * corresponds to the "continent name".  If any value for this three-element
   * aray is missing, supply null for that array elenment.
   */
  public abstract String[] ipAddressToLocation(final String ipAddress);


  // explicit protected access so subclass implementation can use
  protected final Jedis conn;

} // RedisLookupBase
