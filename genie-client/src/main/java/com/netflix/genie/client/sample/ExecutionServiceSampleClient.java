/*
 *
 *  Copyright 2014 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.genie.client.sample;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.netflix.config.ConfigurationManager;
import com.netflix.genie.client.ExecutionServiceClient;
import com.netflix.genie.common.model.ClusterCriteria;
import com.netflix.genie.common.model.FileAttachment;
import com.netflix.genie.common.model.Job;
import com.netflix.genie.common.model.JobStatus;
import java.io.ByteArrayOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sample client demonstrating usage of the Execution Service Client.
 *
 * @author skrishnan
 * @author tgianos
 */
public final class ExecutionServiceSampleClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionServiceSampleClient.class);

    private ExecutionServiceSampleClient() {
        // never called
    }

    /**
     * Main for running client code .
     *
     * @param args
     * @throws java.lang.Exception
     */
    public static void main(final String[] args) throws Exception {

        // Initialize Eureka, if it is being used
        // LOG.info("Initializing Eureka");
        // ExecutionServiceClient.initEureka("test");
        LOG.info("Initializing list of Genie servers");
        ConfigurationManager.getConfigInstance().setProperty("genieClient.ribbon.listOfServers",
                "ec2-54-89-50-113.compute-1.amazonaws.com:7001");

        LOG.info("Initializing ExecutionServiceClient");
        final ExecutionServiceClient client = ExecutionServiceClient.getInstance();

        final String userName = "genietest";
        final String jobName = "sampleClientTestJob";
        LOG.info("Getting jobInfos using specified filter criteria");
        final Multimap<String, String> params = ArrayListMultimap.create();
        params.put("userName", userName);
        params.put("status", JobStatus.FAILED.name());
        params.put("limit", "3");
        for (final Job ji : client.getJobs(params)) {
            LOG.info("Job Info: {id, status, finishTime} - {"
                    + ji.getId() + ", " + ji.getStatus() + ", "
                    + ji.getFinishTime() + "}");
        }

        LOG.info("Running Hive job");
        final Set<String> criteriaTags = new HashSet<String>();
        criteriaTags.add("adhoc");
        final ClusterCriteria criteria = new ClusterCriteria(criteriaTags);
        final List<ClusterCriteria> clusterCriterias = new ArrayList<ClusterCriteria>();
        final Set<String> commandCriteria = new HashSet<String>();
        clusterCriterias.add(criteria);
        commandCriteria.add("prodhive11_mr2");

        Job job = new Job(
                userName,
                jobName,
                "-f hive.q",
                commandCriteria,
                clusterCriterias,
                null);
        job.setDescription("This is a test");
        // send the query as an attachment
        final File query = File.createTempFile("hive", ".q");
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(query, "UTF-8");
            pw.println("select count(*) from counters where dateint=20120430 and hour=10;");
        } finally {
            if (pw != null) {
                pw.close();
            }
        }
        final Set<FileAttachment> attachments = new HashSet<FileAttachment>();
        final FileAttachment attachment = new FileAttachment();
        attachment.setName("hive.q");
        
        FileInputStream fin = null;
        ByteArrayOutputStream bos = null;
        try {
            fin = new FileInputStream(query);
            bos = new ByteArrayOutputStream();
            final byte[] buf = new byte[4096];
            int read;
            while ((read = fin.read(buf)) != -1) {
                bos.write(buf, 0, read);
            }
            attachment.setData(bos.toByteArray());
        } finally {
            if (fin != null) {
                fin.close();
            }
            if (bos != null) {
                bos.close();
            }
        }
        attachments.add(attachment);
        job.setAttachments(attachments);
        job = client.submitJob(job);

        final String jobID = job.getId();
        final String outputURI = job.getOutputURI();
        LOG.info("Job ID: " + jobID);
        LOG.info("Output URL: " + outputURI);

        LOG.info("Getting jobInfo by jobID");
        job = client.getJob(jobID);
        LOG.info(job.toString());

        LOG.info("Waiting for job to finish");
        job = client.waitForCompletion(jobID, 600000, 5000);
        LOG.info("Job status: " + job.getStatus());

        LOG.info("Killing jobs using jobID");
        final Job killedJob = client.killJob(jobID);
        LOG.info("Job status: " + killedJob.getStatus());

        LOG.info("Done");
    }
}