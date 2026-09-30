package com.example.ioedunew.controller;

import com.example.ioedunew.config.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DirectUploadControllerTest {

    @Test
    void completeDeclaresThePathVariableNameExplicitly() throws Exception {
        Method complete = DirectUploadController.class.getMethod("complete", AuthUser.class, String.class);
        PathVariable pathVariable = complete.getParameters()[1].getAnnotation(PathVariable.class);

        assertNotNull(pathVariable);
        assertEquals("id", pathVariable.value());
    }
}
