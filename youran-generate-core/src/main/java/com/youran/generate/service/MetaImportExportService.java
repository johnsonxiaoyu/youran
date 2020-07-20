package com.youran.generate.service;

import com.google.common.collect.ImmutableMap;
import com.youran.common.exception.BusinessException;
import com.youran.common.util.JsonUtil;
import com.youran.generate.config.GenerateProperties;
import com.youran.generate.pojo.dto.MetaEntityFeatureDTO;
import com.youran.generate.pojo.dto.SystemDTO;
import com.youran.generate.pojo.dto.chart.source.JoinDTO;
import com.youran.generate.pojo.dto.chart.source.JoinPartDTO;
import com.youran.generate.pojo.mapper.*;
import com.youran.generate.pojo.mapper.chart.MetaChartMapper;
import com.youran.generate.pojo.mapper.chart.MetaChartSourceItemMapper;
import com.youran.generate.pojo.mapper.chart.MetaChartSourceMapper;
import com.youran.generate.pojo.po.*;
import com.youran.generate.pojo.po.chart.MetaChartPO;
import com.youran.generate.pojo.po.chart.MetaDashboardPO;
import com.youran.generate.pojo.po.chart.source.MetaChartSourcePO;
import com.youran.generate.pojo.po.chart.source.item.MetaChartSourceItemPO;
import com.youran.generate.service.chart.MetaChartService;
import com.youran.generate.service.chart.MetaDashboardService;
import com.youran.generate.service.chart.source.MetaChartSourceService;
import com.youran.generate.service.chart.source.item.MetaChartSourceItemService;
import com.youran.generate.util.Zip4jUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 元数据导入导出服务类
 *
 * @author: cbb
 * @date: 10/12/2019 21:20
 */
@Service
public class MetaImportExportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetaImportExportService.class);
    public static final String PROJECT_JSON_FILE = "project.json";
    public static final String CONST_JSON_FILE = "const.json";
    public static final String CONST_DETAIL_JSON_FILE = "constDetail.json";
    public static final String ENTITY_JSON_FILE = "entity.json";
    public static final String FIELD_JSON_FILE = "field.json";
    public static final String INDEX_JSON_FILE = "index.json";
    public static final String CASCADE_EXT_JSON_FILE = "cascadeExt.json";
    public static final String MTM_JSON_FILE = "manyToMany.json";
    public static final String MTM_CASCADE_EXT_JSON_FILE = "mtmCascadeExt.json";
    public static final String CHART_JSON_FILE = "chart.json";
    public static final String CHART_SOURCE_JSON_FILE = "chartSource.json";
    public static final String CHART_SOURCE_ITEM_JSON_FILE = "chartSourceItem.json";
    public static final String DASHBOARD_JSON_FILE = "dashboard.json";
    public static final String SYSTEM_JSON_FILE = "_system.json";
    @Autowired
    private MetaQueryAssembleService metaQueryAssembleService;
    @Autowired
    private GenerateProperties generateProperties;
    @Autowired
    private MetaProjectService metaProjectService;
    @Autowired
    private MetaConstService metaConstService;
    @Autowired
    private MetaConstDetailService metaConstDetailService;
    @Autowired
    private MetaEntityService metaEntityService;
    @Autowired
    private MetaFieldService metaFieldService;
    @Autowired
    private MetaIndexService metaIndexService;
    @Autowired
    private MetaCascadeExtService metaCascadeExtService;
    @Autowired
    private MetaManyToManyService metaManyToManyService;
    @Autowired
    private MetaMtmCascadeExtService metaMtmCascadeExtService;
    @Autowired
    private MetaChartService metaChartService;
    @Autowired
    private MetaChartSourceService metaChartSourceService;
    @Autowired
    private MetaChartSourceItemService metaChartSourceItemService;
    @Autowired
    private MetaDashboardService metaDashboardService;
    @Autowired
    private DataDirService dataDirService;


    /**
     * 将项目元数据导出成zip
     *
     * @param projectId
     * @return
     */
    public File metaExport(Integer projectId) {
        MetaProjectPO project = metaQueryAssembleService.getAssembledProject(projectId, false);
        String exportDir = dataDirService.getProjectExportDir(project);
        String zipFilePath = exportDir + ".zip";
        File dir = new File(exportDir);
        File outFile = new File(zipFilePath);
        try {
            FileUtils.deleteDirectory(dir);
            FileUtils.deleteQuietly(outFile);
            FileUtils.forceMkdir(dir);
        } catch (IOException e) {
            LOGGER.error("创建导出目录失败", e);
        }
        // 导出项目json文件
        JsonUtil.writeJsonToFile(project, true, new File(dir, PROJECT_JSON_FILE));
        List<MetaConstPO> consts = project.getConsts();
        if (CollectionUtils.isNotEmpty(consts)) {
            // 导出枚举json文件
            JsonUtil.writeJsonToFile(consts, true, new File(dir, CONST_JSON_FILE));
            List<MetaConstDetailPO> details = consts.stream()
                .flatMap(constPO -> constPO.getDetailList().stream())
                .collect(Collectors.toList());
            // 导出枚举值json文件
            JsonUtil.writeJsonToFile(details, true, new File(dir, CONST_DETAIL_JSON_FILE));
        }
        List<MetaEntityPO> entities = project.getEntities();
        if (CollectionUtils.isNotEmpty(entities)) {
            // 导出实体json文件
            JsonUtil.writeJsonToFile(entities, true, new File(dir, ENTITY_JSON_FILE));
            List<MetaFieldPO> fields = entities.stream()
                .flatMap(entityPO -> {
                    Map<Integer, MetaFieldPO> fieldMap = entityPO.getFields();
                    if (MapUtils.isEmpty(fieldMap)) {
                        return Stream.empty();
                    }
                    return fieldMap.values().stream();
                }).collect(Collectors.toList());
            // 导出字段json文件
            JsonUtil.writeJsonToFile(fields, true, new File(dir, FIELD_JSON_FILE));
            List<MetaIndexPO> indexes = entities.stream()
                .flatMap(entityPO -> entityPO.getIndexes().stream())
                .peek(MetaIndexPO::resetFieldIds)
                .collect(Collectors.toList());
            // 导出索引json文件
            JsonUtil.writeJsonToFile(indexes, true, new File(dir, INDEX_JSON_FILE));
            List<MetaCascadeExtPO> cascadeExts = fields.stream()
                .flatMap(fieldPO -> {
                    List<MetaCascadeExtPO> list = fieldPO.getCascadeExts();
                    if (CollectionUtils.isEmpty(list)) {
                        return Stream.empty();
                    }
                    return list.stream();
                })
                .collect(Collectors.toList());
            // 导出外键级联扩展json文件
            JsonUtil.writeJsonToFile(cascadeExts, true, new File(dir, CASCADE_EXT_JSON_FILE));
        }
        List<MetaManyToManyPO> mtms = project.getMtms();
        if (CollectionUtils.isNotEmpty(mtms)) {
            // 导出多对多json文件
            JsonUtil.writeJsonToFile(mtms, true, new File(dir, MTM_JSON_FILE));
            List<MetaMtmCascadeExtPO> mtmCascades = mtms.stream()
                .flatMap(mtm -> {
                    List<MetaMtmCascadeExtPO> list1 = mtm.getCascadeExtList1();
                    List<MetaMtmCascadeExtPO> list2 = mtm.getCascadeExtList2();
                    return Stream.concat(list1.stream(), list2.stream());
                })
                .collect(Collectors.toList());
            // 导出多对多级联扩展json文件
            JsonUtil.writeJsonToFile(mtmCascades, true, new File(dir, MTM_CASCADE_EXT_JSON_FILE));
        }
        List<MetaChartPO> charts = project.getCharts();
        if (CollectionUtils.isNotEmpty(charts)) {
            // 导出图表json文件
            JsonUtil.writeJsonToFile(charts, true, new File(dir, CHART_JSON_FILE));
            Set<MetaChartSourcePO> chartSources = charts.stream().map(MetaChartPO::getChartSource).collect(Collectors.toSet());
            // 导出图表数据源json文件
            JsonUtil.writeJsonToFile(chartSources, true, new File(dir, CHART_SOURCE_JSON_FILE));
            List<MetaChartSourceItemPO> chartSourceItems = chartSources.stream()
                .flatMap(chartSource -> chartSource.fetchSourceItems().stream())
                .collect(Collectors.toList());
            // 导出图表数据项json文件
            JsonUtil.writeJsonToFile(chartSourceItems, true, new File(dir, CHART_SOURCE_ITEM_JSON_FILE));
        }
        List<MetaDashboardPO> dashboards = project.getDashboards();
        if (CollectionUtils.isNotEmpty(dashboards)) {
            // 导出看板json文件
            JsonUtil.writeJsonToFile(dashboards, true, new File(dir, DASHBOARD_JSON_FILE));
        }

        // 导出系统信息json文件
        SystemDTO systemDTO = new SystemDTO(generateProperties.getVersion());
        JsonUtil.writeJsonToFile(systemDTO, true, new File(dir, SYSTEM_JSON_FILE));

        // 将文件夹打成压缩包
        Zip4jUtil.compressFolder(dir, outFile);
        return outFile;
    }

    /**
     * 通过zip压缩包导入项目元数据
     *
     * @param zipFile
     * @return
     */
    @Transactional(rollbackFor = RuntimeException.class)
    public MetaProjectPO metaImport(File zipFile) {
        String importDir = dataDirService.getPathWithoutZipFileSuffix(zipFile);
        // 解压zip包
        Zip4jUtil.extractAll(zipFile, importDir);
        LOGGER.info("将zip包解压到：{}", importDir);
        // json文件所在目录
        String jsonDir = dataDirService.getFirstChildDir(importDir) + File.separator;
        // 读取项目json文件，并解析成po
        MetaProjectPO projectFromJson = JsonUtil.parseObjectFromFile(
            new File(jsonDir + PROJECT_JSON_FILE), MetaProjectPO.class);
        if (projectFromJson == null) {
            throw new BusinessException("导入失败");
        }
        MetaProjectPO project = this.saveProject(projectFromJson);
        Integer projectId = project.getProjectId();

        // 读取常量json文件，并解析成po列表
        List<MetaConstPO> constListFromJson = JsonUtil.parseArrayFromFile(
            new File(jsonDir + CONST_JSON_FILE), MetaConstPO.class);
        List<MetaConstPO> constList = constListFromJson.stream()
            .map(constFromJson -> this.saveConst(constFromJson, projectId))
            .collect(Collectors.toList());
        Map<Integer, Integer> constIdMap = this.getIdMap(constListFromJson, constList, MetaConstPO::getConstId);
        // 读取常量值json文件，并解析成po列表
        List<MetaConstDetailPO> constDetailListFromJson = JsonUtil.parseArrayFromFile(
            new File(jsonDir + CONST_DETAIL_JSON_FILE), MetaConstDetailPO.class);
        if (CollectionUtils.isNotEmpty(constDetailListFromJson)) {
            constDetailListFromJson.stream()
                .forEach(constDetailFromJson -> this.saveConstDetail(constDetailFromJson, constIdMap, projectId));
        }

        // 读取实体json文件，并解析成po列表
        List<MetaEntityPO> entityListFromJson = JsonUtil.parseArrayFromFile(
            new File(jsonDir + ENTITY_JSON_FILE), MetaEntityPO.class);
        List<MetaEntityPO> entityList = entityListFromJson.stream()
            .map(entityFromJson -> this.saveEntity(entityFromJson, projectId))
            .collect(Collectors.toList());
        Map<Integer, Integer> entityIdMap = this.getIdMap(entityListFromJson, entityList, MetaEntityPO::getEntityId);

        // 读取字段json文件，并解析成po列表
        List<MetaFieldPO> fieldListFromJson = JsonUtil.parseArrayFromFile(
            new File(jsonDir + FIELD_JSON_FILE), MetaFieldPO.class);


        List<MetaFieldPO> fieldList = fieldListFromJson.stream()
            .map(fieldFromJson -> this.saveField(fieldFromJson, entityIdMap, projectId))
            .collect(Collectors.toList());
        Map<Integer, Integer> fieldIdMap = this.getIdMap(fieldListFromJson, fieldList, MetaFieldPO::getFieldId);

        // 重置外键字段id
        fieldList.stream()
            .filter(field -> field.getForeignKey() && field.getForeignFieldId() != null)
            .forEach(field -> this.resetForeignFieldId(field, fieldIdMap));

        // 读取索引json文件，并解析成po列表
        List<MetaIndexPO> indexListFromJson = JsonUtil.parseArrayFromFile(
            new File(jsonDir + INDEX_JSON_FILE), MetaIndexPO.class);
        indexListFromJson.stream()
            .map(indexFromJson -> this.saveIndex(indexFromJson, entityIdMap, fieldIdMap, projectId))
            .collect(Collectors.toList());

        // 读取外键级联扩展json文件，并解析成po列表
        List<MetaCascadeExtPO> cascadeExtListFromJson = JsonUtil.parseArrayFromFile(
            new File(jsonDir + CASCADE_EXT_JSON_FILE), MetaCascadeExtPO.class);
        cascadeExtListFromJson.forEach(cascadeExtFromJson ->
            this.saveCascadeExt(cascadeExtFromJson, entityIdMap, fieldIdMap, projectId));

        // 读取多对多json文件，并解析成po列表
        List<MetaManyToManyPO> mtmListFromJson = JsonUtil.parseArrayFromFile(
            new File(jsonDir + MTM_JSON_FILE), MetaManyToManyPO.class);

        List<MetaManyToManyPO> mtmList = mtmListFromJson.stream()
            .map(mtmFromJson -> this.saveMtm(mtmFromJson, projectId, entityIdMap))
            .collect(Collectors.toList());
        Map<Integer, Integer> mtmIdMap = this.getIdMap(mtmListFromJson, mtmList, MetaManyToManyPO::getMtmId);

        // 读取多对多级联扩展json文件，并解析成po列表
        List<MetaMtmCascadeExtPO> mtmCascadeExtListFromJson = JsonUtil.parseArrayFromFile(
            new File(jsonDir + MTM_CASCADE_EXT_JSON_FILE), MetaMtmCascadeExtPO.class);
        mtmCascadeExtListFromJson.stream()
            .forEach(mtmCascadeExtFromJson -> this.saveMtmCascadeExt(mtmCascadeExtFromJson, mtmIdMap,
                entityIdMap, fieldIdMap, projectId));
        // 更新title字段id
        entityList.forEach(metaEntityPO -> this.updateEntityFeature(metaEntityPO, fieldIdMap));

        // 读取图表数据源json文件，并解析成po列表
        List<MetaChartSourcePO> metaChartSourceFromJson = JsonUtil.parseArrayFromFile(
            new File(jsonDir + CHART_SOURCE_JSON_FILE), MetaChartSourcePO.class);
        List<MetaChartSourcePO> metaChartSourceList = metaChartSourceFromJson.stream()
            .map(metaChartSourcePO -> this.saveMetaChartSource(metaChartSourcePO, entityIdMap, fieldIdMap, mtmIdMap, projectId))
            .collect(Collectors.toList());
        Map<Integer, Integer> metaChartSourceIdMap = this.getIdMap(metaChartSourceFromJson, metaChartSourceList, MetaChartSourcePO::getSourceId);

        // 读取图表数据项json文件，并解析成po列表
        List<MetaChartSourceItemPO> metaChartSourceItemFromJson = JsonUtil.parseArrayFromFile(
            new File(jsonDir + CHART_SOURCE_ITEM_JSON_FILE), MetaChartSourceItemPO.class);
        List<MetaChartSourceItemPO> metaChartSourceItemList = metaChartSourceItemFromJson.stream()
            .map(metaChartSourceItemPO -> this.saveMetaChartSourceItem(metaChartSourceItemPO, metaChartSourceIdMap, projectId))
            .collect(Collectors.toList());
        Map<Integer, Integer> metaChartSourceItemIdMap = this.getIdMap(metaChartSourceItemFromJson, metaChartSourceItemList, MetaChartSourceItemPO::getSourceItemId);
        // 更新图表数据项
        metaChartSourceItemList.forEach(sourceItemPO -> this.updateChartSourceItemFeature(sourceItemPO, fieldIdMap, metaChartSourceItemIdMap));

        // 读取图表json文件，并解析成po列表
        List<MetaChartPO> metaChartFromJson = JsonUtil.parseArrayFromFile(
            new File(jsonDir + CHART_JSON_FILE), MetaChartPO.class);
        List<MetaChartPO> metaChartList = metaChartFromJson.stream()
            .map(metaChartSourcePO -> this.saveMetaChart(metaChartSourcePO, metaChartSourceIdMap, metaChartSourceItemIdMap, projectId))
            .collect(Collectors.toList());
        Map<Integer, Integer> metaChartIdMap = this.getIdMap(metaChartFromJson, metaChartList, MetaChartPO::getChartId);


        // todo 导入看板

        return project;
    }


    /**
     * 更新实体特性
     *
     * @param metaEntityPO
     * @param fieldIdMap
     */
    private void updateEntityFeature(MetaEntityPO metaEntityPO, Map<Integer, Integer> fieldIdMap) {
        metaEntityPO.normalize();
        MetaEntityFeatureDTO feature = metaEntityPO.getEntityFeature();
        if (feature.getTitleFieldId() != null) {
            // 替换为新的字段id
            metaEntityService.doUpdateFeature(metaEntityPO,
                ImmutableMap.of("titleFieldId", fieldIdMap.get(feature.getTitleFieldId())));
        }
    }

    /**
     * 把json中解析出来的项目保存到数据库
     *
     * @param projectFromJson
     * @return
     */
    private MetaProjectPO saveProject(MetaProjectPO projectFromJson) {
        MetaProjectPO project = MetaProjectMapper.INSTANCE.copyWithoutRemote(projectFromJson);
        metaProjectService.doSave(project);
        LOGGER.debug("导入项目：{}", JsonUtil.toJSONString(project));
        return project;
    }

    /**
     * 把json中解析出来的枚举保存到数据库
     *
     * @param constFromJson
     * @return
     */
    private MetaConstPO saveConst(MetaConstPO constFromJson, Integer projectId) {
        MetaConstPO constPO = MetaConstMapper.INSTANCE.copy(constFromJson);
        constPO.setProjectId(projectId);
        metaConstService.doSave(constPO);
        LOGGER.debug("导入枚举：{}", JsonUtil.toJSONString(constPO));
        return constPO;
    }

    /**
     * 把json中解析出来的枚举值保存到数据库
     *
     * @param constDetailFromJson
     * @param constIdMap
     * @param projectId
     * @return
     */
    private MetaConstDetailPO saveConstDetail(MetaConstDetailPO constDetailFromJson,
                                              Map<Integer, Integer> constIdMap,
                                              Integer projectId) {
        Integer constId = constIdMap.get(constDetailFromJson.getConstId());
        if (constId == null) {
            LOGGER.error("枚举值json有误：{}", JsonUtil.toJSONString(constDetailFromJson));
            return null;
        }
        MetaConstDetailPO constDetailPO = MetaConstDetailMapper.INSTANCE.copy(constDetailFromJson);
        constDetailPO.setConstId(constId);
        constDetailPO.setProjectId(projectId);
        metaConstDetailService.doSave(constDetailPO);
        LOGGER.debug("导入枚举值：{}", JsonUtil.toJSONString(constDetailPO));
        return constDetailPO;
    }

    /**
     * 把json中解析出来的实体保存到数据库
     *
     * @param entityFromJson
     * @param projectId
     * @return
     */
    private MetaEntityPO saveEntity(MetaEntityPO entityFromJson, Integer projectId) {
        MetaEntityPO entityPO = MetaEntityMapper.INSTANCE.copy(entityFromJson);
        entityPO.setProjectId(projectId);
        metaEntityService.doSave(entityPO);
        LOGGER.debug("导入实体：{}", JsonUtil.toJSONString(entityPO));
        return entityPO;
    }

    /**
     * 把json中解析出来的字段保存到数据库
     *
     * @param fieldFromJson
     * @param entityIdMap
     * @param projectId
     * @return
     */
    private MetaFieldPO saveField(MetaFieldPO fieldFromJson,
                                  Map<Integer, Integer> entityIdMap,
                                  Integer projectId) {
        Integer entityId = entityIdMap.get(fieldFromJson.getEntityId());
        Integer foreignEntityId = entityIdMap.get(fieldFromJson.getForeignEntityId());
        if (entityId == null) {
            LOGGER.error("字段json有误：{}", JsonUtil.toJSONString(fieldFromJson));
            return null;
        }
        MetaFieldPO fieldPO = MetaFieldMapper.INSTANCE.copy(fieldFromJson);
        fieldPO.setEntityId(entityId);
        fieldPO.setForeignEntityId(foreignEntityId);
        fieldPO.setProjectId(projectId);
        metaFieldService.doSave(fieldPO);
        LOGGER.debug("导入字段：{}", JsonUtil.toJSONString(fieldPO));
        return fieldPO;
    }

    /**
     * 重置字段的外键字段id
     *
     * @param field
     * @param fieldIdMap
     */
    private void resetForeignFieldId(MetaFieldPO field, Map<Integer, Integer> fieldIdMap) {
        Integer foreignFieldId = fieldIdMap.get(field.getForeignFieldId());
        if (foreignFieldId == null) {
            LOGGER.error("外键字段有误：{}", field.getForeignFieldId());
            return;
        }
        field.setForeignFieldId(foreignFieldId);
        metaFieldService.doUpdate(field);
    }

    /**
     * 把json中解析出来的索引保存到数据库
     *
     * @param indexFromJson
     * @param entityIdMap
     * @param fieldIdMap
     * @param projectId
     * @return
     */
    private MetaIndexPO saveIndex(MetaIndexPO indexFromJson,
                                  Map<Integer, Integer> entityIdMap,
                                  Map<Integer, Integer> fieldIdMap,
                                  Integer projectId) {
        Integer entityId = entityIdMap.get(indexFromJson.getEntityId());
        if (entityId == null) {
            LOGGER.error("索引json有误：{}", JsonUtil.toJSONString(indexFromJson));
            return null;
        }
        List<Integer> fieldIds = indexFromJson.getFieldIds();
        List<Integer> convertedFieldIds;
        if (CollectionUtils.isNotEmpty(fieldIds)) {
            convertedFieldIds = fieldIds.stream()
                .map(id -> fieldIdMap.get(id))
                .collect(Collectors.toList());
        } else {
            convertedFieldIds = Collections.emptyList();
        }
        MetaIndexPO indexPO = MetaIndexMapper.INSTANCE.copy(indexFromJson);
        indexPO.setEntityId(entityId);
        indexPO.setFieldIds(convertedFieldIds);
        indexPO.setProjectId(projectId);
        metaIndexService.doSave(indexPO);
        LOGGER.debug("导入索引：{}", JsonUtil.toJSONString(indexPO));
        return indexPO;
    }

    /**
     * 把json中解析出来的外键级联扩展保存到数据库
     *
     * @param cascadeExtFromJson
     * @param entityIdMap
     * @param fieldIdMap
     * @param projectId
     * @return
     */
    private MetaCascadeExtPO saveCascadeExt(MetaCascadeExtPO cascadeExtFromJson,
                                            Map<Integer, Integer> entityIdMap,
                                            Map<Integer, Integer> fieldIdMap,
                                            Integer projectId) {
        Integer entityId = entityIdMap.get(cascadeExtFromJson.getEntityId());
        Integer cascadeEntityId = entityIdMap.get(cascadeExtFromJson.getCascadeEntityId());
        Integer fieldId = fieldIdMap.get(cascadeExtFromJson.getFieldId());
        Integer cascadeFieldId = fieldIdMap.get(cascadeExtFromJson.getCascadeFieldId());
        if (entityId == null || cascadeEntityId == null ||
            fieldId == null || cascadeFieldId == null) {
            LOGGER.error("外键级联扩展json有误：{}", JsonUtil.toJSONString(cascadeExtFromJson));
            return null;
        }
        MetaCascadeExtPO cascadeExtPO = MetaCascadeExtMapper.INSTANCE.copy(cascadeExtFromJson);
        cascadeExtPO.setEntityId(entityId);
        cascadeExtPO.setCascadeEntityId(cascadeEntityId);
        cascadeExtPO.setFieldId(fieldId);
        cascadeExtPO.setCascadeFieldId(cascadeFieldId);
        cascadeExtPO.setProjectId(projectId);
        metaCascadeExtService.doSave(cascadeExtPO);
        LOGGER.debug("导入外键级联扩展：{}", JsonUtil.toJSONString(cascadeExtPO));
        return cascadeExtPO;
    }

    /**
     * 把json中解析出来的多对多保存到数据库
     *
     * @param mtmFromJson
     * @param projectId
     * @param entityIdMap
     * @return
     */
    private MetaManyToManyPO saveMtm(MetaManyToManyPO mtmFromJson,
                                     Integer projectId,
                                     Map<Integer, Integer> entityIdMap) {
        Integer entityId1 = entityIdMap.get(mtmFromJson.getEntityId1());
        Integer entityId2 = entityIdMap.get(mtmFromJson.getEntityId2());
        if (entityId1 == null || entityId2 == null) {
            LOGGER.error("多对多json有误：{}", JsonUtil.toJSONString(mtmFromJson));
            return null;
        }
        MetaManyToManyPO mtmPO = MetaManyToManyMapper.INSTANCE.copy(mtmFromJson);
        mtmPO.setProjectId(projectId);
        mtmPO.setEntityId1(entityId1);
        mtmPO.setEntityId2(entityId2);
        metaManyToManyService.parseMtmFeature(mtmPO);
        metaManyToManyService.doSave(mtmPO);
        LOGGER.debug("导入多对多：{}", JsonUtil.toJSONString(mtmPO));
        return mtmPO;
    }

    /**
     * 把json中解析出来的多对多级联扩展保存到数据库
     *
     * @param mtmCascadeExtFromJson
     * @param mtmIdMap
     * @param entityIdMap
     * @param fieldIdMap
     * @param projectId
     * @return
     */
    private MetaMtmCascadeExtPO saveMtmCascadeExt(MetaMtmCascadeExtPO mtmCascadeExtFromJson,
                                                  Map<Integer, Integer> mtmIdMap,
                                                  Map<Integer, Integer> entityIdMap,
                                                  Map<Integer, Integer> fieldIdMap,
                                                  Integer projectId) {
        Integer mtmId = mtmIdMap.get(mtmCascadeExtFromJson.getMtmId());
        Integer entityId = entityIdMap.get(mtmCascadeExtFromJson.getEntityId());
        Integer cascadeEntityId = entityIdMap.get(mtmCascadeExtFromJson.getCascadeEntityId());
        Integer cascadeFieldId = fieldIdMap.get(mtmCascadeExtFromJson.getCascadeFieldId());
        if (entityId == null || cascadeEntityId == null ||
            mtmId == null || cascadeFieldId == null) {
            LOGGER.error("多对多级联扩展json有误：{}", JsonUtil.toJSONString(mtmCascadeExtFromJson));
            return null;
        }
        MetaMtmCascadeExtPO mtmCascadeExtPO = MetaMtmCascadeExtMapper.INSTANCE.copy(mtmCascadeExtFromJson);
        mtmCascadeExtPO.setEntityId(entityId);
        mtmCascadeExtPO.setCascadeEntityId(cascadeEntityId);
        mtmCascadeExtPO.setMtmId(mtmId);
        mtmCascadeExtPO.setCascadeFieldId(cascadeFieldId);
        mtmCascadeExtPO.setProjectId(projectId);
        metaMtmCascadeExtService.doSave(mtmCascadeExtPO);
        LOGGER.debug("导入多对多级联扩展：{}", JsonUtil.toJSONString(mtmCascadeExtPO));
        return mtmCascadeExtPO;
    }

    /**
     * 把json中解析出来的图表数据源保存到数据库
     *
     * @param metaChartSourceFromJson
     * @param entityIdMap
     * @param fieldIdMap
     * @param mtmIdMap
     * @param projectId
     * @return
     */
    private MetaChartSourcePO saveMetaChartSource(MetaChartSourcePO metaChartSourceFromJson,
                                                  Map<Integer, Integer> entityIdMap,
                                                  Map<Integer, Integer> fieldIdMap,
                                                  Map<Integer, Integer> mtmIdMap,
                                                  Integer projectId) {
        MetaChartSourcePO chartSourcePO = MetaChartSourceMapper.INSTANCE.copy(metaChartSourceFromJson);
        chartSourcePO.setProjectId(projectId);
        chartSourcePO.featureDeserialize();
        chartSourcePO.setEntityId(entityIdMap.get(chartSourcePO.getEntityId()));
        List<JoinDTO> joins = chartSourcePO.getJoins();
        if (CollectionUtils.isNotEmpty(joins)) {
            for (JoinDTO join : joins) {
                this.convertIdsForJoinPartDTO(join.getLeft(), entityIdMap, fieldIdMap, mtmIdMap);
                this.convertIdsForJoinPartDTO(join.getRight(), entityIdMap, fieldIdMap, mtmIdMap);
            }
        }
        chartSourcePO.featureSerialize();
        metaChartSourceService.doSave(chartSourcePO);
        LOGGER.debug("导入图表数据源：{}", JsonUtil.toJSONString(chartSourcePO));
        return chartSourcePO;
    }

    private void convertIdsForJoinPartDTO(JoinPartDTO joinPartDTO,
                                          Map<Integer, Integer> entityIdMap,
                                          Map<Integer, Integer> fieldIdMap,
                                          Map<Integer, Integer> mtmIdMap) {
        if (joinPartDTO == null) {
            return;
        }
        if (joinPartDTO.getEntityId() != null) {
            joinPartDTO.setEntityId(entityIdMap.get(joinPartDTO.getEntityId()));
        }
        if (joinPartDTO.getFieldId() != null) {
            joinPartDTO.setFieldId(fieldIdMap.get(joinPartDTO.getFieldId()));
        }
        if (joinPartDTO.getMtmId() != null) {
            joinPartDTO.setMtmId(mtmIdMap.get(joinPartDTO.getMtmId()));
        }
    }

    /**
     * 把json中解析出来的图表数据项保存到数据库
     *
     * @param metaChartSourceItemFromJson
     * @param metaChartSourceIdMap
     * @param projectId
     * @return
     */
    private MetaChartSourceItemPO saveMetaChartSourceItem(MetaChartSourceItemPO metaChartSourceItemFromJson,
                                                          Map<Integer, Integer> metaChartSourceIdMap,
                                                          Integer projectId) {
        Integer sourceId = metaChartSourceIdMap.get(metaChartSourceItemFromJson.getSourceId());
        if (sourceId == null) {
            LOGGER.error("图表数据项json有误：{}", JsonUtil.toJSONString(metaChartSourceItemFromJson));
            return null;
        }
        MetaChartSourceItemPO chartSourceItemPO = MetaChartSourceItemMapper.INSTANCE.copy(metaChartSourceItemFromJson);
        chartSourceItemPO.setProjectId(projectId);
        chartSourceItemPO.setSourceId(sourceId);
        metaChartSourceItemService.doSave(chartSourceItemPO);
        LOGGER.debug("导入图表数据项：{}", JsonUtil.toJSONString(chartSourceItemPO));
        return chartSourceItemPO;
    }

    /**
     * 更新图表数据项
     *
     * @param sourceItemPO
     * @param fieldIdMap
     * @param metaChartSourceItemIdMap
     */
    private void updateChartSourceItemFeature(MetaChartSourceItemPO sourceItemPO,
                                              Map<Integer, Integer> fieldIdMap,
                                              Map<Integer, Integer> metaChartSourceItemIdMap) {
        boolean changed = false;
        if (sourceItemPO.getParentId() != null) {
            sourceItemPO.setParentId(metaChartSourceItemIdMap.get(sourceItemPO.getParentId()));
            changed = true;
        }
        MetaChartSourceItemPO metaChartSourceItemPO = sourceItemPO.castSubType(true);
        changed |= metaChartSourceItemPO.convertFieldId(fieldIdMap);
        if (changed) {
            metaChartSourceItemPO.featureSerialize();
            metaChartSourceItemService.doUpdate(metaChartSourceItemPO);
        }
    }

    /**
     * 把json中解析出来的图表保存到数据库
     *
     * @param metaChartSourceFromJson
     * @param metaChartSourceIdMap
     * @param metaChartSourceItemIdMap
     * @param projectId
     * @return
     */
    private MetaChartPO saveMetaChart(MetaChartPO metaChartSourceFromJson,
                                      Map<Integer, Integer> metaChartSourceIdMap,
                                      Map<Integer, Integer> metaChartSourceItemIdMap,
                                      Integer projectId) {
        Integer sourceId = metaChartSourceIdMap.get(metaChartSourceFromJson.getSourceId());
        if (sourceId == null) {
            LOGGER.error("图表json有误：{}", JsonUtil.toJSONString(metaChartSourceFromJson));
            return null;
        }
        MetaChartPO chartPO = MetaChartMapper.INSTANCE.copy(metaChartSourceFromJson);
        chartPO.setProjectId(projectId);
        MetaChartPO subType = chartPO.castSubType(true);
        subType.convertItemId(metaChartSourceItemIdMap);
        subType.featureSerialize();
        metaChartService.doSave(subType);
        LOGGER.debug("导入图表：{}", JsonUtil.toJSONString(subType));
        return subType;
    }

    /**
     * 获取主键映射表
     *
     * @param poListFromJson 从json文件中解析出来的元素列表
     * @param poList         持久化后的元素列表
     * @param idGetter       主键获取函数
     * @param <T>            列表元素类型
     * @param <R>            主键类型
     * @return
     */
    private <T, R> Map<R, R> getIdMap(List<T> poListFromJson, List<T> poList,
                                      Function<T, R> idGetter) {
        Map<R, R> idMap = new HashMap<>(poListFromJson.size());
        for (int i = 0; i < poListFromJson.size(); i++) {
            T poFromJson = poListFromJson.get(i);
            T po = poList.get(i);
            if (poFromJson != null && po != null) {
                idMap.put(idGetter.apply(poFromJson), idGetter.apply(po));
            }
        }
        return idMap;
    }


}
