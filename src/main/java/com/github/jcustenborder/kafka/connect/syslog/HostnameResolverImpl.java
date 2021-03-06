/**
 * Copyright © 2016 Jeremy Custenborder (jcustenborder@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jcustenborder.kafka.connect.syslog;

import com.github.jcustenborder.kafka.connect.syslog.config.BaseSyslogSourceConfig;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

class HostnameResolverImpl implements HostnameResolver {
  private static final Logger log = LoggerFactory.getLogger(HostnameResolverImpl.class);
  final BaseSyslogSourceConfig config;
  final Cache<InetAddress, InetSocketAddress> reverseDnsCache;

  public HostnameResolverImpl(BaseSyslogSourceConfig config) {
    this.config = config;
    reverseDnsCache = CacheBuilder.newBuilder()
        .expireAfterWrite(this.config.reverseDnsCacheTtl(), TimeUnit.MILLISECONDS)
        .recordStats()
        .build();
  }


  @Override

  public InetSocketAddress resolve(SocketAddress socketAddress) {
    Preconditions.checkNotNull(socketAddress, "socketAddress should not be null.");
    Preconditions.checkState(socketAddress instanceof InetSocketAddress, "socketAddress should be InetSocketAddress.");
    final InetSocketAddress inetSocketAddress = (InetSocketAddress) socketAddress;

    try {
      return reverseDnsCache.get(inetSocketAddress.getAddress(), new Callable<InetSocketAddress>() {
        @Override
        public InetSocketAddress call() throws Exception {
          String hostname = inetSocketAddress.getHostName();

          if (inetSocketAddress.isUnresolved()) {
            throw new IllegalStateException(
                String.format("Could not resolve %s", inetSocketAddress.getAddress())
            );
          }

          if (log.isDebugEnabled()) {
            log.debug("Resolved {} to {}. Caching result", inetSocketAddress.getAddress(), hostname);
          }
          return inetSocketAddress;
        }
      });
    } catch (UncheckedExecutionException ex) {
      if (log.isDebugEnabled()) {
        log.debug("Could not resolve {}.", inetSocketAddress.getAddress(), ex.getCause());
      }
      return inetSocketAddress;
    } catch (ExecutionException e) {
      if (log.isWarnEnabled()) {
        log.warn("Exception thrown while resolving ip for {}", inetSocketAddress.getAddress(), e);
      }
      return inetSocketAddress;
    }
  }
}
