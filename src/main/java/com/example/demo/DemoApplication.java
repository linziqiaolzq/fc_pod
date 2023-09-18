package com.example.demo;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class DemoApplication {

	@Autowired
	private InnerService innerService;

	private static final String CLUSTER_ID = "clusterId";
	private static final String REGION_ID = "regionId";

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@RequestMapping("/*")
	public String pushPodNumberMeteringData(@RequestHeader Map<String, String> headers,
			@RequestParam(required = false) Map<String, String> params, @RequestBody(required = false) String body) throws Exception {
		// 本代码为计算巢全托管部署，已有ACK场景上报Pod数场景，调用时需要填入集群Id与地域Id信息
		// 地域信息，若为国内填写杭州(cn-hangzhou)，国际站填写新加坡(ap-southeast-1)
		System.out.printf("---------------------------------- PushPodNumber Start ----------------------------------%n");

		String clusterId = params.get(CLUSTER_ID);
		String regionId = params.get(REGION_ID);
		if (StringUtils.isBlank(clusterId) || StringUtils.isBlank(regionId)) {
			return "Parameters service id or region id is missing.";
		}
		String kubeConfigPath = innerService.getKubeConfig(headers, clusterId, regionId);
		System.out.printf("Get clusterId %s kubeConfig success.%n", clusterId);

		List<String> namespaces = innerService.getNamespace(kubeConfigPath);
		System.out.printf("Get push namespace success: %s.%n", namespaces);

		int successCount = 0;
		for (String namespace : namespaces) {
			try {
				Integer podNumber = innerService.getPodNumber(kubeConfigPath, namespace);
				System.out.printf("GetPodNumber Success, namespace %s, podNumber %s.%n", namespace, podNumber);
				Integer count = innerService.pushMeteringData(headers, podNumber, namespace, regionId);
				successCount = successCount + count;
			} catch (Exception e) {
				System.out.printf("GetPodNumber namespace %s Error: %s.%n", namespace, e);
			}
		}

		System.out.printf("PushSuccess Success, successCount: %s.%n", successCount);
		System.out.printf("---------------------------------- PushPodNumber End ----------------------------------%n");
		return "PushSuccess: serviceInstance Number " + successCount;
	}
}