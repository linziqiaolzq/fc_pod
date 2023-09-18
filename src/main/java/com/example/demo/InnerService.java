package com.example.demo;

import java.util.List;
import java.util.Map;


public interface InnerService {

    /**
     *  获取k8s的kubeconfig
     */
    String getKubeConfig(Map<String, String> headers, String clusterId, String regionId) throws Exception;

    /**
     * 获取集群中名称为服务实例Id的namespace
     */
    List<String> getNamespace(String kubeConfigPath) throws Exception;

    /**
     * 获取Pod数量
     */
    Integer getPodNumber(String kubeConfigPath, String namespace) throws Exception;

    /**
     * 推送计量数据
     */
    Integer pushMeteringData(Map<String, String> headers, Integer podNumber,
        String serviceInstanceId, String regionId) throws Exception;
}
