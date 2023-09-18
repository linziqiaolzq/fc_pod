package com.example.demo.Impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.aliyun.computenestsupplier20210521.models.PushMeteringDataRequest;
import com.aliyun.computenestsupplier20210521.models.PushMeteringDataResponse;
import com.aliyun.cs20151215.Client;
import com.aliyun.cs20151215.models.DescribeClusterUserKubeconfigRequest;
import com.aliyun.cs20151215.models.DescribeClusterUserKubeconfigResponse;
import com.aliyun.teaopenapi.models.Config;
import com.example.demo.InnerService;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@Service
public class InnerServiceImpl implements InnerService {

    private static final String ACCESS_ID = "x-fc-access-key-id";
    private static final String ACCESS_SECRET = "x-fc-access-key-secret";
    private static final String SECURITY_TOKEN = "x-fc-security-token";
    private static final String SERVICE_INSTANCE_PREFIX = "si-";
    private static final int SERVICE_INSTANCE_ID_LENGTH = 23;
    private static final int SUCCESS_NUMBER = 1;
    private static final int NOT_PUSH_NUMBER = 0;
    private static final String METERING_FORMAT = "[{\"StartTime\":\"%s\",\"EndTime\":\"%s\","
        + "\"Entities\":[{\"Key\":\"Unit\",\"Value\":\"%s\"}]}]";

    @Override
    public String getKubeConfig(Map<String, String> headers, String clusterId, String regionId) throws Exception {
        String accessKeyId = headers.get(ACCESS_ID);
        String accessKeySecret = headers.get(ACCESS_SECRET);
        String securityToken = headers.get(SECURITY_TOKEN);
        Client client = createClient(accessKeyId, accessKeySecret, securityToken, regionId);
        DescribeClusterUserKubeconfigRequest request = new DescribeClusterUserKubeconfigRequest();
        request.setTemporaryDurationMinutes(15L);
        DescribeClusterUserKubeconfigResponse response = client.describeClusterUserKubeconfig(clusterId, request);
        if (response == null || response.getBody() == null || response.getBody().getConfig() == null) {
            String errorMessage = "Cluster id %s not found config.";
            throw new Exception(String.format(errorMessage, clusterId));
        }
        return response.getBody().getConfig();
    }

    private static Client createClient(String accessKeyId, String accessKeySecret, String securityToken,
        String regionId) throws Exception {
        Config config = new Config();
        config.accessKeyId = accessKeyId;
        config.accessKeySecret = accessKeySecret;
        config.securityToken = securityToken;
        config.regionId = regionId;
        return new Client(config);
    }

    @Override
    public List<String> getNamespace(String kubeConfigPath) throws Exception {
        Yaml yaml = new Yaml(new SafeConstructor());
        Object config = yaml.load(kubeConfigPath);
        Map<String, Object> configMap = (Map)config;
        String currentContext = (String)configMap.get("current-context");
        ArrayList<Object> contexts = (ArrayList)configMap.get("contexts");
        ArrayList<Object> clusters = (ArrayList)configMap.get("clusters");
        ArrayList<Object> users = (ArrayList)configMap.get("users");
        Object preferences = configMap.get("preferences");
        KubeConfig kubeConfig = new KubeConfig(contexts, clusters, users);
        kubeConfig.setContext(currentContext);
        kubeConfig.setPreferences(preferences);

        ApiClient apiClient = ClientBuilder.kubeconfig(kubeConfig).build();
        CoreV1Api api = new CoreV1Api(apiClient);

        V1NamespaceList list = api.listNamespace(null, null, null, null, null, null, null, null, null, null);
        List<String> namespace = new ArrayList<>();

        for (V1Namespace v1Namespace : list.getItems()) {
            if (StringUtils.isNotBlank(v1Namespace.getMetadata().getName()) &&
                (!v1Namespace.getMetadata().getName().startsWith(SERVICE_INSTANCE_PREFIX)
                    || v1Namespace.getMetadata().getName().length() != SERVICE_INSTANCE_ID_LENGTH)) {
                continue;
            }
            namespace.add(v1Namespace.getMetadata().getName());
        }
        return namespace;
    }

    @Override
    public Integer getPodNumber(String kubeConfigPath, String namespace) throws Exception {
        Yaml yaml = new Yaml(new SafeConstructor());
        Object config = yaml.load(kubeConfigPath);
        Map<String, Object> configMap = (Map)config;
        String currentContext = (String)configMap.get("current-context");
        ArrayList<Object> contexts = (ArrayList)configMap.get("contexts");
        ArrayList<Object> clusters = (ArrayList)configMap.get("clusters");
        ArrayList<Object> users = (ArrayList)configMap.get("users");
        Object preferences = configMap.get("preferences");
        KubeConfig kubeConfig = new KubeConfig(contexts, clusters, users);
        kubeConfig.setContext(currentContext);
        kubeConfig.setPreferences(preferences);

        ApiClient apiClient = ClientBuilder.kubeconfig(kubeConfig).build();
        CoreV1Api api = new CoreV1Api(apiClient);

        //invokes the CoreV1Api client
        V1PodList list = api.listNamespacedPod(namespace, null, null, null, null, null, null, null, null, null, null);
        return list.getItems().size();
    }

    private com.aliyun.computenestsupplier20210521.Client createComputeNestClient(String accessKeyId, String accessKeySecret,
        String securityToken, String regionId) throws Exception {
        Config config = new Config();
        config.accessKeyId = accessKeyId;
        config.accessKeySecret = accessKeySecret;
        config.securityToken = securityToken;
        // 国内杭州(cn-hangzhou)，国外新加坡(ap-southeast-1)
        config.regionId = regionId;
        return new com.aliyun.computenestsupplier20210521.Client(config);
    }

    @Override
    public Integer pushMeteringData(Map<String, String> headers, Integer podNumber,
        String serviceInstanceId, String regionId) throws Exception {
        if (podNumber == 0) {
            System.out.printf("PushMeteringData serviceInstanceId %s return, pod number is 0.%n", serviceInstanceId);
            return NOT_PUSH_NUMBER;
        }
        Date date = new Date();
        Long endTime = date.getTime() / 1000;
        Long startTime = date.getTime() / 1000 - 1;

        String metering = String.format(METERING_FORMAT, startTime, endTime, podNumber);
        System.out.printf("PushMeteringData serviceInstanceId %s, metering %s.%n", serviceInstanceId, metering);

        PushMeteringDataRequest request = new PushMeteringDataRequest();
        request.setMetering(metering);
        request.setServiceInstanceId(serviceInstanceId);

        String accessKeyId = headers.get(ACCESS_ID);
        String accessKeySecret = headers.get(ACCESS_SECRET);
        String securityToken = headers.get(SECURITY_TOKEN);
        com.aliyun.computenestsupplier20210521.Client client =
            createComputeNestClient(accessKeyId, accessKeySecret, securityToken, regionId);
        PushMeteringDataResponse response = client.pushMeteringData(request);
        System.out.printf("PushMeteringData serviceInstanceId %s, Success: %s.%n",
            serviceInstanceId, response.getBody().getRequestId());
        return SUCCESS_NUMBER;
    }
}
