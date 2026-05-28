package com.example.udtbe.streaming.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.udtbe.common.fixture.StreamingJobMetricFixture;
import com.example.udtbe.common.support.DataJpaSupport;
import com.example.udtbe.domain.streaming.entity.StreamingJobMetric;
import com.example.udtbe.domain.streaming.entity.enums.StreamingJobType;
import com.example.udtbe.domain.streaming.repository.StreamingJobMetricRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StreamingJobMetricRepositoryTest extends DataJpaSupport {

    @Autowired
    private StreamingJobMetricRepository streamingJobMetricRepository;

    @DisplayName("배치 작업 메트릭을 저장하고 조회할 수 있다.")
    @Test
    void saveAndFindAll() {
        // given
        StreamingJobMetric metric1 = StreamingJobMetricFixture.completedJob(1L,
                StreamingJobType.REGISTER, 100);
        StreamingJobMetric metric2 = StreamingJobMetricFixture.partialCompetedJob(2L,
                StreamingJobType.UPDATE, 100, 40);

        streamingJobMetricRepository.saveAll(List.of(metric1, metric2));

        // when
        List<StreamingJobMetric> metrics = streamingJobMetricRepository.findAll();

        // then
        assertThat(metrics).hasSize(2);
    }
}