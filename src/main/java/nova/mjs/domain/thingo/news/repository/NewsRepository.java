package nova.mjs.domain.thingo.news.repository;

import nova.mjs.domain.thingo.news.entity.News;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface NewsRepository extends JpaRepository<News, Long> {

    //카테고리 기준으로 탐색
    Page<News> findByCategory(News.Category category, Pageable pageable);

    //카테고리를 기준으로 삭제
    void deleteByCategory(News.Category category);

    // 페이지 단위로 중복 조회 (newsIndex만 가져옴)
    @Query("select n.newsIndex from News n where n.newsIndex in :idxList")
    List<Long> findExistingNewsIndexIn(@Param("idxList") Collection<Long> idxList);

}
