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

package org.apache.hadoop.fs.store;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.shell.CommandFormat;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.Tool;

import static org.apache.hadoop.fs.store.StoreUtils.split;

/**
 * Entry point for store applications
 */
public class StoreEntryPoint extends Configured implements Tool {

  private static final Logger LOG = LoggerFactory.getLogger(StoreEntryPoint.class);

  /**
   * Exit code when a usage message was printed: {@value}.
   */
  public static int EXIT_USAGE = StoreExitCodes.E_USAGE;

  protected CommandFormat commandFormat;

  private PrintStream out = System.out;

  @Override
  public int run(String[] args) throws Exception {
    return 0;
  }

  public PrintStream getOut() {
    return out;
  }

  public void setOut(PrintStream out) {
    this.out = out;
  }

  /**
   * Print a formatted string followed by a newline to the output stream.
   * @param format format string
   * @param args optional arguments
   */
  public void println(String format, Object... args) {
    out.println(String.format(format, args));
    out.flush();
  }

  public void warn(String format, Object... args) {
    out.println("WARNING: " + String.format(format, args));
    out.flush();
  }

  public static void errorln(String format, Object... args) {
    System.err.println(String.format(format, args));
    System.err.flush();
  }

  public void heading(String format, Object... args) {
    String text = String.format(format, args);
    int l = text.length();
    StringBuilder sb = new StringBuilder(l);
    for (int i = 0; i < l; i++) {
      sb.append("=");
    }
    println("\n%s\n%s\n", text, sb.toString());
  }

  protected static void exit(int status, String text) {
    ExitUtil.terminate(status, text);
  }

  protected static void exit(StoreExitException ex) {
    ExitUtil.terminate(ex.getExitCode(), ex.getMessage());
  }

  public CommandFormat getCommandFormat() {
    return commandFormat;
  }

  public void setCommandFormat(CommandFormat commandFormat) {
    this.commandFormat = commandFormat;
  }

  /**
   * Parse CLI arguments and returns the position arguments.
   * The options are stored in {@link #commandFormat}.
   *
   * @param args command line arguments.
   * @return the position arguments from CLI.
   */
  protected List<String> parseArgs(String[] args) {
    return args.length > 0 ? getCommandFormat().parse(args, 0)
        : new ArrayList<>(0);
  }

  /**
   * Get the value of a key-val option.
   * @param opt option.
   * @return the value or null
   */
  protected String getOption(String opt) {
    return getCommandFormat().getOptValue(opt);
  }

  /**
   * Did the command line have a specific option.
   * @param opt option.
   * @return true iff it was set.
   */
  protected boolean hasOption(String opt) {
    return getCommandFormat().getOpt(opt);
  }

  /**
   * Get the value of a key-val option.
   * @param opt option.
   * @return the value or null
   */
  protected Optional<String> getOptional(String opt) {
    return Optional.ofNullable(getCommandFormat().getOptValue(opt));
  }
  /**
   * Add all the various configuration files.
   */
  protected void addAllDefaultXMLFiles() {
    Configuration.addDefaultResource("hdfs-default.xml");
    Configuration.addDefaultResource("hdfs-site.xml");
    // this order is what JobConf does via
    // org.apache.hadoop.mapreduce.util.ConfigUtil.loadResources()
    Configuration.addDefaultResource("mapred-default.xml");
    Configuration.addDefaultResource("mapred-site.xml");
    Configuration.addDefaultResource("yarn-default.xml");
    Configuration.addDefaultResource("yarn-site.xml");
  }

  /**
   * For subclasses: exit after a throwable was raised.
   * @param ex exception caught
   */
  protected static void exitOnThrowable(Throwable ex) {
    if (ex instanceof CommandFormat.UnknownOptionException) {
      errorln(ex.getMessage());
      exit(EXIT_USAGE, ex.getMessage());
    } else if (ex instanceof StoreExitException) {
      LOG.debug("Command failure", ex);
      exit((StoreExitException) ex);
    } else {
      ex.printStackTrace(System.err);
      exit(StoreExitCodes.E_ERROR, ex.toString());
    }
  }

  protected void maybeAddXMLFileOption(
      final Configuration conf,
      final String opt)
      throws FileNotFoundException, MalformedURLException {
    String xmlfile = getOption(opt);
    if (xmlfile != null) {
      File f = new File(xmlfile);
      if (!f.exists()) {
        throw new FileNotFoundException(f.toString());
      }
      println("Adding XML configuration file %s", f);
      conf.addResource(f.toURI().toURL());
    }
  }

  /**
   * Add a token file from the command line.
   * @param opt option
   * @throws IOException failures
   */
  protected void maybeAddTokens(String opt) throws IOException {
    String tokenfile = getOption(opt);
    if (tokenfile != null) {
      heading("Adding tokenfile %s", tokenfile);
      Credentials credentials = Credentials.readTokenStorageFile(
          new File(tokenfile), getConf());
      Collection<Token<? extends TokenIdentifier>> tokens
          = credentials.getAllTokens();
      println("Loaded %d token(s)", tokens.size());
      for (Token<? extends TokenIdentifier> token : tokens) {
        println(token.toString());
      }
      UserGroupInformation.getCurrentUser().addCredentials(credentials);
    }
  }

  protected void maybePatchDefined(final Configuration conf, final String opt) {
    getOptional(opt).ifPresent(d -> {
          Map.Entry<String, String> pair = split(d, "true");
          conf.set(pair.getKey(), pair.getValue());
        });
  }


}
