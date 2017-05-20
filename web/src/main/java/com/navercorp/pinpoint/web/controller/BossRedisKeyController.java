package com.navercorp.pinpoint.web.controller;

import com.navercorp.pinpoint.common.bo.AnnotationBo;
import com.navercorp.pinpoint.common.bo.Span;
import com.navercorp.pinpoint.common.bo.SpanBo;
import com.navercorp.pinpoint.common.bo.SpanEventBo;
import com.navercorp.pinpoint.common.hbase.HbaseTemplate2;
import com.navercorp.pinpoint.common.trace.AnnotationKeyMatcher;
import com.navercorp.pinpoint.web.calltree.span.*;
import com.navercorp.pinpoint.web.dao.TraceDao;
import com.navercorp.pinpoint.web.mapper.SpanMapper;
import com.navercorp.pinpoint.web.service.AnnotationKeyMatcherService;
import com.navercorp.pinpoint.web.service.FilteredMapService;
import com.navercorp.pinpoint.web.service.SpanResult;
import com.navercorp.pinpoint.web.vo.LimitedScanResult;
import com.navercorp.pinpoint.web.vo.Range;
import com.navercorp.pinpoint.web.vo.TransactionId;
import com.sematext.hbase.wd.AbstractRowKeyDistributor;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.hbase.client.Scan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.hadoop.hbase.RowMapper;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;

/**
 * Created by zhaoyalong on 16-6-23.
 */
@Controller
@RequestMapping("/boss")
/**
 * http://192.168.1.167:28080/boss/redisKey.pinpoint?startTime=1466524800&endTime=1466647559000
 * * http://192.168.1.167:28080/boss/rs.pinpoint?s=1466524800000&e=1466647559000&limit=1000&appName=166mcs
 */
public class BossRedisKeyController {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    HbaseTemplate2 hbaseTemplate2;

    @Autowired
    AnnotationKeyMatcherService annotationKeyMatcherService;

    @Autowired
    @Qualifier("traceDistributor")
    AbstractRowKeyDistributor rowKeyDistributor;

    @Autowired
    FilteredMapService filteredMapService;

    @Autowired
    TraceDao traceDao;

    @RequestMapping("/redisKey")
    public ModelAndView redisKey(String sr, String er) throws IOException {
        RowMapper<List<SpanBo>> s = new SpanMapper();
        Scan scan = new Scan();
        final TransactionId srId = new TransactionId(sr);
        final TransactionId erId = new TransactionId(er);
        scan.setStartRow(rowKeyDistributor.getDistributedKey(srId.getBytes()));
        scan.setStopRow(rowKeyDistributor.getDistributedKey(erId.getBytes()));
        scan.addFamily("S".getBytes(Charset.defaultCharset()));
        scan.addFamily("A".getBytes(Charset.defaultCharset()));
        scan.addFamily("T".getBytes(Charset.defaultCharset()));
        List<List<SpanBo>> list = hbaseTemplate2.find("Traces", scan, s);
        if (list != null && !list.isEmpty()) {
            for (List<SpanBo> boList : list) {
                try {
                    if (boList != null && boList.size() > 0) {
                        SpanBo spanBo = boList.get(0);
                        if (spanBo.getSpanEventBoList() != null && spanBo.getSpanEventBoList().size() > 0) {
                            List<SpanEventBo> spanEventBoList = spanBo.getSpanEventBoList();
                            if (spanEventBoList != null && spanEventBoList.size() > 0) {
                                for (SpanEventBo spanEventBo : spanEventBoList) {
                                    if (spanEventBo != null) {
                                        if ("REDIS".equals(spanEventBo.getDestinationId())) {
                                            List<AnnotationBo> annotationBos = spanEventBo.getAnnotationBoList();
                                            if (annotationBos != null && !annotationBos.isEmpty()) {
                                                String str = annotationBos.get(0).getValue().toString();
                                                if (str.contains("params") && str.contains("boss") && str.length() < 100) {
                                                    str = StringUtils.substringAfterLast(annotationBos.get(0).getValue().toString(), "params=");
                                                    if (!str.contains(",")) {
                                                        logger.warn(str.replaceAll("\\d+", "?"));
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {

                }
            }
        }
        return null;
    }

    private String getArgument(final SpanAlign align) {
        if (align.isSpan()) {
            return getRpcArgument(align.getSpanBo());
        }
        return getDisplayArgument(align.getSpanEventBo());
    }

    private String getRpcArgument(SpanBo spanBo) {
        String rpc = spanBo.getRpc();
        if (rpc != null) {
            return rpc;
        }
        return getDisplayArgument(spanBo);
    }

    private String getDisplayArgument(Span span) {
        AnnotationBo displayArgument = getDisplayArgument0(span);
        if (displayArgument == null) {
            return "";
        }
        return Objects.toString(displayArgument.getValue(), "");
    }

    private AnnotationBo getDisplayArgument0(Span span) {
        // TODO needs a more generalized implementation for Arcus
        List<AnnotationBo> list = span.getAnnotationBoList();
        if (list == null) {
            return null;
        }
        final AnnotationKeyMatcher matcher = annotationKeyMatcherService.findAnnotationKeyMatcher(span.getServiceType());
        if (matcher == null) {
            return null;
        }
        for (AnnotationBo annotation : list) {
            int key = annotation.getKey();

            if (matcher.matches(key)) {
                return annotation;
            }
        }
        return null;
    }

    @RequestMapping("/rs")
    public void rs(@RequestParam(defaultValue = "app-08-boss-mcs", required = false) String appName, long s, long e, @RequestParam(defaultValue = "1000", required = false) int limit) {
        final Range range = new Range(s, e);
        final LimitedScanResult<List<TransactionId>> limitedScanResult = filteredMapService.selectTraceIdsFromApplicationTraceIndex(appName, range, limit);
        List<List<SpanBo>> list = traceDao.selectAllSpans(limitedScanResult.getScanData());
        if (list != null && !list.isEmpty()) {
            for (List<SpanBo> boList : list) {
                try {
                    if (boList != null && boList.size() > 0) {
                        SpanBo spanBo = boList.get(0);
                        if (spanBo.getSpanEventBoList() != null && spanBo.getSpanEventBoList().size() > 0) {
                            List<SpanEventBo> spanEventBoList = spanBo.getSpanEventBoList();
                            if (spanEventBoList != null && spanEventBoList.size() > 0) {
                                for (SpanEventBo spanEventBo : spanEventBoList) {
                                    if (spanEventBo != null) {
                                        if ("REDIS".equals(spanEventBo.getDestinationId())) {
                                            List<AnnotationBo> annotationBos = spanEventBo.getAnnotationBoList();
                                            if (annotationBos != null && !annotationBos.isEmpty()) {
                                                String str = annotationBos.get(0).getValue().toString();
                                                if (str.contains("params") && str.contains("boss") && str.length() < 100) {
                                                    str = StringUtils.substringAfterLast(annotationBos.get(0).getValue().toString(), "params=");
                                                    if (!str.contains(",")) {
                                                        logger.warn(str.replaceAll("\\d+", "?") + " " + spanBo.getElapsed());
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e1) {

                }
            }
        }
    }
}
