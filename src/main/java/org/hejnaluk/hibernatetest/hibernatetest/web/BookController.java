package org.hejnaluk.hibernatetest.hibernatetest.web;

import org.hejnaluk.hibernatetest.hibernatetest.repository.BookSummary;
import org.hejnaluk.hibernatetest.hibernatetest.service.LibraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/books")
@RequiredArgsConstructor
public class BookController {

    private final LibraryService libraryService;

    @GetMapping("/naive")
    public List<BookDto> naive() {
        return libraryService.listBooksNaive();
    }

    @GetMapping("/fetch-join")
    public List<BookDto> fetchJoin() {
        return libraryService.listBooksWithFetchJoin();
    }

    @GetMapping("/summaries")
    public List<BookSummary> summaries() {
        return libraryService.listSummaries();
    }

    @GetMapping("/{id}")
    public BookDto get(@PathVariable Long id) {
        return libraryService.getBook(id);
    }

    @PatchMapping("/{id}/price")
    public BookDto changePrice(@PathVariable Long id, @RequestParam BigDecimal value) {
        return libraryService.changePrice(id, value);
    }

    public record NewReview(int rating, String comment) {
    }

    @PostMapping("/{id}/reviews")
    public BookDto addReview(@PathVariable Long id, @RequestBody NewReview review) {
        return libraryService.addReview(id, review.rating(), review.comment());
    }
}
