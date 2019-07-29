/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.fs.tools;

import java.util.List;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.BucketPolicy;
import com.amazonaws.services.s3.model.GetS3AccountOwnerRequest;
import com.amazonaws.services.s3.model.Owner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3a.InternalAccess;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.shell.CommandFormat;
import org.apache.hadoop.fs.store.DurationInfo;
import org.apache.hadoop.fs.store.StoreEntryPoint;
import org.apache.hadoop.util.ToolRunner;

import static org.apache.hadoop.fs.store.CommonParameters.DEFINE;
import static org.apache.hadoop.fs.store.CommonParameters.TOKENFILE;
import static org.apache.hadoop.fs.store.CommonParameters.XMLFILE;
import static org.apache.hadoop.fs.store.StoreExitCodes.E_USAGE;

/*
org.apache.hadoop.fs.tools.BucketState.
 */
public class BucketState extends StoreEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(BucketState.class);

  public static final String LIMIT = "limit";

  public static final String USAGE
      = "Usage: list\n"
      + optusage(DEFINE, "key=value", "Define a property")
      + optusage(TOKENFILE, "file", "Hadoop token file to load")
      + optusage(XMLFILE, "file", "XML config file to load")
      + " <S3A path>";

  public BucketState() {
    createCommandFormat(1, 1);
    addValueOptions(TOKENFILE, XMLFILE, DEFINE);
  }

  @Override
  public int run(String[] args) throws Exception {
    List<String> paths = parseArgs(args);
    if (paths.size() != 1) {
      errorln(USAGE);
      return E_USAGE;
    }

    addAllDefaultXMLFiles();
    maybeAddTokens(TOKENFILE);
    final Configuration conf = new Configuration();

    maybeAddXMLFileOption(conf, XMLFILE);
    maybePatchDefined(conf, DEFINE);

    final Path source = new Path(paths.get(0));
    println("");
    final DurationInfo duration = new DurationInfo(LOG, "Bucket State");
    try {
      S3AFileSystem fs = (S3AFileSystem) source.getFileSystem(conf);
      InternalAccess internals = new InternalAccess(fs);
      AmazonS3 s3Client = internals.getAmazonS3Client();
      Owner owner = s3Client.getS3AccountOwner(
          new GetS3AccountOwnerRequest());
      println("Bucket owner is %s (ID=%s)", owner.getDisplayName(),
          owner.getId());
      BucketPolicy policy = s3Client.getBucketPolicy(fs.getBucket());
      String policyText = policy.getPolicyText();
      println("Bucket policy:%n%s",
          policyText != null ? policyText : "NONE" );
    } finally {
      duration.close();
    }
    return 0;
  }

  /**
   * Execute the command, return the result or throw an exception,
   * as appropriate.
   * @param args argument varags.
   * @return return code
   * @throws Exception failure
   */
  public static int exec(String... args) throws Exception {
    return ToolRunner.run(new BucketState(), args);
  }

  /**
   * Main entry point. Calls {@code System.exit()} on all execution paths.
   * @param args argument list
   */
  public static void main(String[] args) {
    try {
      exit(exec(args), "");
    } catch (Throwable e) {
      exitOnThrowable(e);
    }
  }

}
