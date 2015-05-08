package org.ideaedu

import idea.data.rest.*
import java.util.*
import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType
import groovy.json.JsonBuilder

/**
 * The Main class provides a way to test the REST API by retrieving the complete report model (report model,
 * all response data points, the survey, and the question groups). It has some optional command line arguments that
 * control the behavior. The arguments include:
 * <ul>
 * <li>h (host) - the hostname of the IDEA REST Server</li>
 * <li>p (port) - the port that is open on the IDEA REST Server</li>
 * <li>b (basePath) - the base path within the IDEA REST Server</li>
 * <li>rid (reportID) - the report ID</li>
 * <li>v (verbose) - provide more output on the command line</li>
 * <li>s (ssl) - use SSL when connecting to the IDEA REST API.</li>
 * <li>st (stat) - collect stats as the report model is loaded.</li>
 * <li>a (app) - the client application name</li>
 * <li>k (key) - the client application key</li>
 * <li>? (help) - show the usage of this</li>
 * </ul>
 *
 * @author Todd Wallentine todd AT theideacenter org
 */
public class Main {

	private static final def DEFAULT_REPORT_ID = 1
	private static final def DEFAULT_HOSTNAME = "localhost"
	private static final def DEFAULT_PORT = 8091
	private static final def DEFAULT_BASE_PATH = "IDEA-REST-SERVER/v1"
	private static final def DEFAULT_AUTH_HEADERS = [ "X-IDEA-APPNAME": "", "X-IDEA-KEY": "" ]
	private static final def MAX_REQUESTS = 10
	private static final def WAIT_TIME_IN_SEC = 1
	private static final def DEFAULT_PROTOCOL = "http"

	private static def hostname = DEFAULT_HOSTNAME
	private static def protocol = DEFAULT_PROTOCOL
	private static def port = DEFAULT_PORT
	private static def basePath = DEFAULT_BASE_PATH
	private static def reportID = DEFAULT_REPORT_ID
	private static def authHeaders = DEFAULT_AUTH_HEADERS

	private static def verboseOutput = false
	private static def collectStats = false

	private static def stats = [:]

	private static RESTClient restClient

	public static void main(String[] args) {

		/*
		 * TODO Other command line options that might be useful:
		 * 1) app name (to include in header)
		 * 2) app key (to include in header)
		 */
		def cli = new CliBuilder( usage: 'Main -v -s -st -h host -p port -b basePath -rid reportID -a "TestClient" -k "ABCDEFG123456"' )
		cli.with {
			v longOpt: 'verbose', 'verbose output'
			s longOpt: 'ssl', 'use SSL (default: false)'
			st longOpt: 'stat', 'provide statistics (default: false)'
			h longOpt: 'host', 'host name (default: localhost)', args:1
			p longOpt: 'port', 'port number (default: 8091)', args:1
			b longOpt: 'basePath', 'base REST path (default: IDEA-REST-SERVER/v1/', args:1
			rid longOpt: 'reportID', 'report ID', args:1
			a longOpt: 'app', 'client application name', args:1
			k longOpt: 'key', 'client application key', args:1
			'?' longOpt: 'help', 'help'
		}
		def options = cli.parse(args)
		if(options.'?') {
			cli.usage()
			return
		}
		if(options.v) {
			verboseOutput = true
		}
		if(options.st) {
			collectStats = true
		}
		if(options.h) {
			hostname = options.h
		}
		if(options.p) {
			port = options.p.toInteger()
		}
		if(options.b) {
			basePath = options.b
		}
		if(options.rid) {
			reportID = options.rid.toInteger()
		}
		if(options.s) {
			protocol = "https"
		}
		if(options.a) {
			authHeaders['X-IDEA-APPNAME'] = options.a
		}
		if(options.k) {
			authHeaders['X-IDEA-KEY'] = options.k
		}

		def startTime = new Date().time
		def reportModel = getCompleteReportModel(reportID)
		def endTime = new Date().time
		stats["Runtime"] = endTime - startTime

		println "Report model for report ${reportID} loaded."

		if(collectStats) {
			printStats(stats)
		}
	}

	static def printStats(stats) {
		if(stats) {
			stats.each { statName, statValue ->
				println "${statName} = ${statValue}ms"
			}
		}
	}

	static def getCompleteReportModel(reportID) {
		def restDataModelMap = [:]
		def reportModel
		def restSurvey
		def questionGroups

		reportModel = getReportModel(reportID)

		if(reportModel) {

			def surveyID = reportModel.survey_id
			restSurvey = getSurvey(surveyID)
			questionGroups = getQuestionGroups(restSurvey)

			reportModel?.aggregate_data?.response_data_points?.each { responseDataPoint ->
				def questionID = responseDataPoint.question_id
				def restDataModel = getReportData(reportID, questionID)
				if(restDataModel) {
					restDataModelMap[questionID] = restDataModel
				}
				// TODO Should we throw a ModelNotFoundException if restDataModel is null? -todd 23Jan2014
			}
		} else {
			println "Unable to get the report model."
		}

		return reportModel
	}

	static def getSurvey(final int surveyID) {
		def survey

		def startTime = new Date().time

		def client = getRESTClient()
		def response = client.get(
			path: "${basePath}/survey/${surveyID}",
			requestContentType: ContentType.JSON,
			headers: authHeaders)
		if(response.status == 200) {
			if(verboseOutput) {
				println "Survey data: ${response.data}"
			}
			survey = response.data
		} else {
			println "An error occured while getting the survey with ID ${surveyID}: ${response.status}"
		}

		def endTime = new Date().time
		stats['Get Survey'] = endTime - startTime

		return survey
	}

	static def getReportModel(reportID) {
		def reportModel

		def startTime = new Date().time

		def client = getRESTClient()
		def response = client.get(
			path: "${basePath}/report/${reportID}/model",
			requestContentType: ContentType.JSON,
			headers: authHeaders)
		if(response.status == 200) {
			if(verboseOutput) {
				println "Report model data: ${response.data}"
			}
			reportModel = response.data
		} else {
			println "An error occured while getting the report model with ID ${reportID}: ${response.status}"
		}

		def endTime = new Date().time
		stats['Get Model'] = endTime - startTime

		return reportModel
	}

	static def getQuestionGroups(survey) {
		def questionGroups = [:]

		def startTime = new Date().time

		def formID = survey.rater_form.id

		def client = getRESTClient()
		def response = client.get(
			path: "${basePath}/forms/${formID}/questions",
			requestContentType: ContentType.JSON,
			headers: authHeaders)
		if(response.status == 200) {
			if(verboseOutput) {
				println "Question group data: ${response.data}"
			}
			questionGroups = response.data.data
		} else {
			println "An error occured while getting the questions for form with ID ${formID}: ${response.status}"
		}

		def endTime = new Date().time
		stats['Get Questions'] = endTime - startTime

		return questionGroups
	}

	static def getReportData(reportID, questionID) {
		def reportData

		def startTime = new Date().time

		def client = getRESTClient()
		def response = client.get(
			path: "${basePath}/report/${reportID}/model/${questionID}",
			requestContentType: ContentType.JSON,
			headers: authHeaders)
		if(response.status == 200) {
			if(verboseOutput) {
				println "Response data point: ${response.data}"
			}
			reportData = response.data
		} else {
			println "An error occured while getting the response data for report ID ${reportID} and question ID ${questionID}: ${response.status}"
		}

		def endTime = new Date().time
		def runTime = endTime - startTime
		stats["Get Data ${questionID}"] = runTime
		if(stats["Get Data"]) {
			stats["Get Data"] += runTime
		} else {
			stats["Get Data"] = runTime
		}

		return reportData
	}

	/**
	 * Get an instance of the RESTClient that can be used to access the REST API.
	 *
	 * @return RESTClient An instance that can be used to access the REST API.
	 */
	private static RESTClient getRESTClient() {
		if(restClient == null) {
			if(verboseOutput) println "REST requests will be sent to ${hostname} on port ${port}"
			restClient = new RESTClient("${protocol}://${hostname}:${port}/")
			restClient.handler.failure = { response ->
				if(verboseOutput) {
					println "The REST call failed: ${response.status}"
				}
				return response
			}
		}
		return restClient
	}
}