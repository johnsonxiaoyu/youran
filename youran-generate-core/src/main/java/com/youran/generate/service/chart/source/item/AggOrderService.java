package com.youran.generate.service.chart.source.item;

import com.youran.common.optimistic.OptimisticLock;
import com.youran.generate.dao.chart.MetaChartSourceItemDAO;
import com.youran.generate.pojo.dto.chart.source.item.AggOrderAddDTO;
import com.youran.generate.pojo.dto.chart.source.item.AggOrderUpdateDTO;
import com.youran.generate.pojo.mapper.chart.MetaChartSourceItemMapper;
import com.youran.generate.pojo.po.chart.source.item.AggOrderPO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author: cbb
 * @date: 2020-05-05
 */
@Service
public class AggOrderService {

    @Autowired
    private MetaChartSourceItemDAO metaChartSourceItemDAO;
    @Autowired
    private MetaChartSourceItemService metaChartSourceItemService;

    /**
     * 校验【聚合排序】数据合法性
     *
     * @param po
     */
    public void check(AggOrderPO po) {
        // TODO
    }

    /**
     * 新增【聚合排序】
     *
     * @param addDTO
     * @return
     */
    @Transactional(rollbackFor = RuntimeException.class)
    public AggOrderPO save(AggOrderAddDTO addDTO) {
        AggOrderPO po = MetaChartSourceItemMapper.INSTANCE
            .fromAggOrderAddDTO(addDTO);
        this.check(po);
        metaChartSourceItemDAO.save(po);
        return po;
    }


    /**
     * 修改【图表数据源项】
     *
     * @param updateDTO
     * @return
     */
    @Transactional(rollbackFor = RuntimeException.class)
    @OptimisticLock
    public AggOrderPO update(AggOrderUpdateDTO updateDTO) {
        Integer sourceItemId = updateDTO.getSourceItemId();
        AggOrderPO po = metaChartSourceItemService.getMetaChartSourceItem(sourceItemId, true);
        MetaChartSourceItemMapper.INSTANCE.setAggOrderUpdateDTO(po, updateDTO);
        this.check(po);
        metaChartSourceItemDAO.update(po);
        return po;
    }


}