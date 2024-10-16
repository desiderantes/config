/**
 * Copyright (C) 2011-2021, Config project contributors
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.typesafe.config.impl;

import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.parser.ConfigNodePath;
import com.typesafe.config.parser.ConfigNodeSyntax;
import com.typesafe.config.parser.ConfigNodeVisitor;

import java.util.List;

// This is a "virtual" AST node that is synthesized from ConfigNodeSimpleValue to improve traversability of the AST
final class ConfigNodeReference implements com.typesafe.config.parser.ConfigNodeReference {

    private final boolean optional;
    private final List<ConfigNodeSyntax> path;
    private final ConfigOrigin origin;

    ConfigNodeReference(ConfigOrigin origin, List<ConfigNodeSyntax> collect, boolean optional) {
        this.optional = optional;
        this.path = collect;
        this.origin = origin;
    }

    @Override
    public String render() {
        return "${" + (this.optional ? "?" : "")
                + Tokenizer.render(this.path.stream().map(x -> ((ConfigNodeSingleToken) x).token()).iterator()) + "}";
    }

    @Override
    public <T> T accept(ConfigNodeVisitor<T> visitor) {
        return visitor.visitReference(this);
    }

    @Override
    public ConfigNodePath getPath() {
        return new ConfigNodeUnparsedPath(path, origin);
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    @Override
    public ConfigOrigin origin() {
        return origin;
    }
}
