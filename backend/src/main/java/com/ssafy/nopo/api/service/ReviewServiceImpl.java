package com.ssafy.nopo.api.service;

import com.ssafy.nopo.api.request.ReviewReq;
import com.ssafy.nopo.api.response.ReviewRes;
import com.ssafy.nopo.common.exception.CustomException;
import com.ssafy.nopo.common.exception.ErrorCode;
import com.ssafy.nopo.db.entity.*;
import com.ssafy.nopo.db.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService{
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final RestoRepository restoRepository;
    private final ReviewImgRepository imgRepository;
    private final VisitedRepository visitedRepository;
    private S3Service s3Service;
    @Override
    public void createReview(ReviewReq reviewReq, List<String> imgUrls, String userId) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new CustomException(ErrorCode.NOT_FOUND_USER_INFO));
        OldRestaurant resto = restoRepository.findById(reviewReq.getRestoId()).orElseThrow(
                () -> new CustomException(ErrorCode.NOT_FOUND_RESTO_INFO));
        if(reviewRepository.findByRestoIdAndUserId(resto.getId(), user.getId()).isPresent())
            throw new CustomException(ErrorCode.ALREADY_POST_REVIEW_ERROR);
        Review review = Review.builder()
                .content(reviewReq.getContent())
                .rating(reviewReq.getRating())
                .user(user)
                .resto(resto)
                .regdate(LocalDateTime.now())
                .build();
        reviewRepository.save(review);

        if(imgUrls != null && !imgUrls.isEmpty()){
            for(String url : imgUrls){
                ReviewImg img = ReviewImg.builder()
                        .review(review)
                        .url(url)
                        .build();
                imgRepository.save(img);
            }
        }
        // 리뷰 남기면 방문한 곳에 데이터 추가
        if(!visitedRepository.findByRestoIdAndUserId(resto.getId(), userId).isPresent()){
            Visited visited = Visited.builder().resto(resto).user(user).build();
            visitedRepository.save(visited);
        }
    }
    @Transactional
    @Override
    public ReviewRes findByReviewId(int reviewId) {
        Review review = reviewRepository.findById(reviewId).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND_REVIEW_INFO));

        // 이미지 목록 불러오기
        List<String> imageUrl = imgRepository.findAllByReviewId(review.getId())
                                .stream()
                                .map(ReviewImg::getUrl)
                                .collect(Collectors.toList());

        String regdate = review.getRegdate().toLocalDate().toString();
        return new ReviewRes(reviewId, imageUrl, review.getContent(), review.getRating(), regdate, review.getUser().getNickname(), review.getResto().getRestoName());
    }

    @Transactional
    @Override
    public void modifyReview(int reviewId, ReviewReq reviewReq, String userId) {
        Review review = reviewRepository.findById(reviewId).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND_REVIEW_INFO));
        if(!review.getUser().getId().equals(userId))
            throw new CustomException(ErrorCode.REVIEW_UPDATE_WRONG_ACCESS);
        review.update(reviewReq.getContent(), reviewReq.getRating());
    }

    @Transactional
    @Override
    public void deleteReview(int reviewId, String userId) {
        Review review = reviewRepository.findById(reviewId).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND_REVIEW_INFO));
        if(!review.getUser().getId().equals(userId))
            throw new CustomException(ErrorCode.REVIEW_DELETE_WRONG_ACCESS);
        try {
            s3Service.deleteAll(review.getImgList());
        } catch (NullPointerException e){
          e.printStackTrace();
        } finally {
            reviewRepository.deleteById(reviewId);
        }
    }
}
