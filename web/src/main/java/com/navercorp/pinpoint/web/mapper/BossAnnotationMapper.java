package com.navercorp.pinpoint.web.mapper;

import com.navercorp.pinpoint.common.bo.AnnotationBo;
import com.navercorp.pinpoint.common.bo.AnnotationBoList;
import com.navercorp.pinpoint.common.buffer.Buffer;
import com.navercorp.pinpoint.common.buffer.OffsetFixedBuffer;
import com.navercorp.pinpoint.common.hbase.HBaseTables;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.client.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.hadoop.hbase.RowMapper;

import java.util.*;

/**
 * Created by zhaoyalong on 16-6-23.
 */
public class BossAnnotationMapper implements RowMapper<List<AnnotationBo>> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public List<AnnotationBo> mapRow(Result result, int rowNum) throws Exception {
        if (result.isEmpty()) {
            return Collections.emptyList();
        }
        List<AnnotationBo> allAnnoList = new ArrayList<AnnotationBo>();
        Cell[] rawCells = result.rawCells();
        for (Cell cell : rawCells) {

            Buffer buffer = new OffsetFixedBuffer(cell.getQualifierArray(), cell.getQualifierOffset());
            long spanId = buffer.readLong();

            if (CellUtil.matchingFamily(cell, HBaseTables.TRACES_CF_ANNOTATION)) {
                int valueLength = cell.getValueLength();
                if (valueLength == 0) {
                    continue;
                }

                buffer.setOffset(cell.getValueOffset());
                AnnotationBoList annotationBoList = new AnnotationBoList();
                annotationBoList.readValue(buffer);
                if (annotationBoList.size() > 0) {
                    annotationBoList.setSpanId(spanId);
                    allAnnoList.addAll(annotationBoList.getAnnotationBoList());
//                    annotationList.put(spanId, annotationBoList.getAnnotationBoList());
                }
            }
        }
        return allAnnoList;
    }
}
