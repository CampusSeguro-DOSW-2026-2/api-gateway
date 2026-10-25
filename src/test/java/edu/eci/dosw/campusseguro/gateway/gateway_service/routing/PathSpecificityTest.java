package edu.eci.dosw.campusseguro.gateway.gateway_service.routing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PathSpecificityTest {

    @Test
    void literalPrefixStopsAtTheFirstWildcard() {
        assertThat(PathSpecificity.literalPrefix("/api/v1/users/**")).isEqualTo("/api/v1/users");
        assertThat(PathSpecificity.literalPrefix("/api/v1/users/{id}")).isEqualTo("/api/v1/users");
        assertThat(PathSpecificity.literalPrefix("/api/v1/roles")).isEqualTo("/api/v1/roles");
    }

    @Test
    void moreSpecificPrefixIsEvaluatedFirst() {
        assertThat(PathSpecificity.orderOf("/api/v1/users/status/**"))
                .isLessThan(PathSpecificity.orderOf("/api/v1/users/**"));
        assertThat(PathSpecificity.orderOf("/api/v1/users/**"))
                .isLessThan(PathSpecificity.orderOf("/api/v1/**"));
    }
}
