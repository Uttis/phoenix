/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.end2end;

import org.apache.hadoop.hbase.HConstants;
import org.apache.phoenix.coprocessor.BaseScannerRegionObserver;
import org.apache.phoenix.iterate.ScanningResultPostValidResultCaller;
import org.apache.phoenix.query.BaseTest;
import org.apache.phoenix.query.QueryServices;
import org.apache.phoenix.schema.StaleRegionBoundaryCacheException;
import org.apache.phoenix.util.PropertiesUtil;
import org.apache.phoenix.util.ReadOnlyProps;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.apache.phoenix.util.TestUtil.TEST_PROPERTIES;

@Category(NeedsOwnMiniClusterTest.class)
public class BaseAggregateWithRegionMoves4IT extends BaseAggregateWithRegionMoves2IT {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(BaseAggregateWithRegionMoves4IT.class);

    private static int countOfValidResults = 0;

    @Before
    public void setUp() throws Exception {
        hasTestStarted = true;
    }

    @After
    public void tearDown() throws Exception {
        countOfDummyResults = 0;
        countOfValidResults = 0;
        TABLE_NAMES.clear();
        hasTestStarted = false;
    }

    private static class TestScanningResultPostValidResultCaller extends
            ScanningResultPostValidResultCaller {

        @Override
        public void postValidRowProcess() {
            if (hasTestStarted && countOfValidResults <= 1) {
                LOGGER.info("Splitting regions of tables {}. current count of valid results: {}",
                        TABLE_NAMES, countOfValidResults);
                countOfValidResults++;
                TABLE_NAMES.forEach(table -> {
                    try {
                        splitAllRegionsOfTable(table, 2);
                    } catch (Exception e) {
                        LOGGER.error("Unable to move regions of table: {}", table);
                    }
                });
            }
        }
    }

    @BeforeClass
    public static synchronized void doSetup() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(BaseScannerRegionObserver.PHOENIX_MAX_LOOKBACK_AGE_CONF_KEY,
                Integer.toString(60 * 60)); // An hour
        props.put(QueryServices.USE_STATS_FOR_PARALLELIZATION, Boolean.toString(false));
        props.put(QueryServices.PHOENIX_SERVER_PAGE_SIZE_MS, Long.toString(0));
        props.put(QueryServices.TESTS_MINI_CLUSTER_NUM_REGION_SERVERS, String.valueOf(2));
        props.put(HConstants.HBASE_CLIENT_SCANNER_MAX_RESULT_SIZE_KEY, String.valueOf(1));
        props.put(QueryServices.PHOENIX_POST_VALID_PROCESS,
                TestScanningResultPostValidResultCaller.class.getName());
        setUpTestDriver(new ReadOnlyProps(props.entrySet().iterator()));
    }

    @AfterClass
    public static synchronized void freeResources() throws Exception {
        BaseTest.freeResourcesIfBeyondThreshold();
    }

    @Test
    public void testAvgGroupByOrderPreservingWithNoStats() throws Exception {
        Properties props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(getUrl(), props);
        String tableName = generateUniqueName();
        initAvgGroupTable(conn, tableName, "");
        testAvgGroupByOrderPreserving(conn, tableName, 5);
    }

    @Test
    public void testSumGroupByOrderPreservingDescWithoutSplit() throws Exception {
        try {
            super.testSumGroupByOrderPreservingDescWithoutSplit();
        } catch (StaleRegionBoundaryCacheException e) {
            hasTestStarted = false;
            LOGGER.error("Rows could not be scanned because of stale region boundary. Try again.");
            super.testSumGroupByOrderPreservingDescWithoutSplit();
        }
    }

    @Test
    public void testGroupByOrderByDescBug3451() throws Exception {
        try {
            super.testGroupByOrderByDescBug3451();
        } catch (StaleRegionBoundaryCacheException e) {
            hasTestStarted = false;
            LOGGER.error("Rows could not be scanned because of stale region boundary. Try again.");
            super.testGroupByOrderByDescBug3451();
        }
    }

}
