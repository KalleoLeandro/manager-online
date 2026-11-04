package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.repository.LocalImageRepository;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@ActiveProfiles("test")
class ArchitectureTests {

    @Test
    void databaseRepositoriesUseNamedParameterJdbcTemplate() {
        for (var bean : context.getBeansWithAnnotation(Repository.class).values()) {
            var type = AopUtils.getTargetClass(bean);
            var fields = Arrays.stream(type.getDeclaredFields())
                .map(field -> field.getType().getName())
                .toList();
            assertThat(fields).doesNotContain(JdbcTemplate.class.getName());
            if (!type.equals(LocalImageRepository.class)) assertThat(fields).contains(
                NamedParameterJdbcTemplate.class.getName()
            );
        }
    }

    @Autowired
    ApplicationContext context;

    @Test
    void controllersAndServicesRespectTheMvcLayerBoundaries() {
        for (var bean : context.getBeansWithAnnotation(RestController.class).values()) {
            var type = AopUtils.getTargetClass(bean);
            assertThat(type.getPackageName()).isEqualTo("br.com.manager.controller");
            assertThat(type.getDeclaredClasses())
                .as(type.getSimpleName() + " keeps models outside controllers")
                .isEmpty();
            assertThat(
                Arrays.stream(type.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers()))
            ).allSatisfy(field -> assertThat(field.getType().getPackageName()).isEqualTo("br.com.manager.service"));
        }
        for (var bean : context.getBeansWithAnnotation(Service.class).values()) {
            var type = AopUtils.getTargetClass(bean);
            assertThat(type.getPackageName()).isEqualTo("br.com.manager.service");
            assertThat(Arrays.stream(type.getDeclaredFields()).map(f -> f.getType().getPackageName())).noneMatch(
                name ->
                    name.startsWith("org.springframework.jdbc") ||
                    name.equals("br.com.manager.controller") ||
                    name.equals("br.com.manager.websocket")
            );
        }
    }
}
