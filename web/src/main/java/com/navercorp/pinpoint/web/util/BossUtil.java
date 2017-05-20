package com.navercorp.pinpoint.web.util;

import com.navercorp.pinpoint.common.bo.*;
import com.navercorp.pinpoint.common.hbase.HbaseTemplate2;
import com.navercorp.pinpoint.common.trace.AnnotationKey;
import com.navercorp.pinpoint.common.trace.AnnotationKeyMatcher;
import com.navercorp.pinpoint.common.util.AnnotationUtils;
import com.navercorp.pinpoint.common.util.ApiDescription;
import com.navercorp.pinpoint.web.calltree.span.*;
import com.navercorp.pinpoint.web.dao.TraceDao;
import com.navercorp.pinpoint.web.mapper.BossAnnotationMapper;
import com.navercorp.pinpoint.web.mapper.SpanMapper;
import com.navercorp.pinpoint.web.service.FilteredMapService;
import com.navercorp.pinpoint.web.service.SpanResult;
import com.navercorp.pinpoint.web.vo.LimitedScanResult;
import com.navercorp.pinpoint.web.vo.Range;
import com.navercorp.pinpoint.web.vo.TransactionId;
import com.navercorp.pinpoint.web.vo.callstacks.Record;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.CompareFilter;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.filter.SubstringComparator;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.data.hadoop.hbase.RowMapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;

/**
 * Created by zhaoyalong on 16-6-22.
 */

public class BossUtil {
    private static final String[] CONFIG_FILES = new String[]{"classpath*:applicationContext-hbase.xml"};

    private static ClassPathXmlApplicationContext context = null;

    /**
     * @param args
     */
    public static void main(String[] args) throws IOException {
        context = new ClassPathXmlApplicationContext(CONFIG_FILES);
        context.start();
        final Range range = new Range(0, 1466666232998L);
        final LimitedScanResult<List<TransactionId>> limitedScanResult = context.getBean(FilteredMapService.class).selectTraceIdsFromApplicationTraceIndex("zyl1", range, 1000);
        List<List<SpanBo>> list = context.getBean(TraceDao.class).selectAllSpans(limitedScanResult.getScanData());
//        List<List<SpanBo>> list = context.getBean(HbaseTemplate2.class).find("Traces", scan, s);
        if (list != null && !list.isEmpty()) {
            for (List<SpanBo> boList : list) {
                try {
                    SpanAligner2 spanAligner = new SpanAligner2(boList, 0);
                    CallTree callTree = spanAligner.sort();
                    SpanResult spanResult = new SpanResult(spanAligner.getMatchType(), callTree.iterator());
                    CallTreeIterator callTreeIterator = spanResult.getCallTree();
                    while (callTreeIterator.hasNext()) {
                        final CallTreeNode node = callTreeIterator.next();
                        final SpanAlign align = node.getValue();
                        SpanEventBo spanEventBo = align.getSpanEventBo();
                        if (spanEventBo != null) {
                            if ("REDIS".equals(spanEventBo.getDestinationId())) {
                                List<AnnotationBo> annotationBos = spanEventBo.getAnnotationBoList();
                                if (annotationBos != null && !annotationBos.isEmpty()) {
                                    String str = annotationBos.get(0).getValue().toString();
                                    if (str.contains("params") && str.contains("boss") && str.length() < 100) {
                                        str = StringUtils.substringAfterLast(annotationBos.get(0).getValue().toString(), "params=");
                                        if (!str.contains(",")) {
//                                            logger.warn(str.replaceAll("\\d+", "?"));
                                            System.out.println(str.replaceAll("\\d+", "?"));
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
    }

    private static String getArgument(final SpanAlign align) {
        if (align.isSpan()) {
            return getRpcArgument(align.getSpanBo());
        }
        return getDisplayArgument(align.getSpanEventBo());
    }

    private static String getRpcArgument(SpanBo spanBo) {
        String rpc = spanBo.getRpc();
        if (rpc != null) {
            return rpc;
        }
        return getDisplayArgument(spanBo);
    }

    private static String getDisplayArgument(Span span) {
        AnnotationBo displayArgument = getDisplayArgument0(span);
        if (displayArgument == null) {
            return "";
        }
        return Objects.toString(displayArgument.getValue(), "");
    }

    private static AnnotationBo getDisplayArgument0(Span span) {
        // TODO needs a more generalized implementation for Arcus
        List<AnnotationBo> list = span.getAnnotationBoList();
        if (list == null) {
            return null;
        }

//        final AnnotationKeyMatcher matcher = annotationKeyMatcherService.findAnnotationKeyMatcher(span.getServiceType());
//        if (matcher == null) {
//            return null;
//        }
//
//        for (AnnotationBo annotation : list) {
//            int key = annotation.getKey();
//
//            if (matcher.matches(key)) {
//                return annotation;
//            }
//        }
        return null;
    }

}
