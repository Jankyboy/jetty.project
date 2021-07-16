//
// ========================================================================
// Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.websocket.javax.common;

import java.util.Map;

/**
 * Optional interface for custom {@link javax.websocket.EndpointConfig} implementations
 * in Jetty to expose Path Param values used during the {@link JavaxWebSocketFrameHandler}
 * resolution of methods.
 * <p>
 * Mostly a feature of the JSR356 Server implementation and its {@code &#064;javax.websocket.server.PathParam} annotation.
 * </p>
 */
public interface PathParamProvider
{
    Map<String, String> getPathParams();
}
