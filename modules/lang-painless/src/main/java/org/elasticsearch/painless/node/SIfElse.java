/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.painless.node;

import org.elasticsearch.painless.Definition;
import org.elasticsearch.painless.Variables;
import org.objectweb.asm.Label;
import org.elasticsearch.painless.MethodWriter;

/**
 * Represents an if/else block.
 */
public final class SIfElse extends AStatement {

    AExpression condition;
    final SBlock ifblock;
    final SBlock elseblock;

    public SIfElse(int line, int offset, String location, AExpression condition, SBlock ifblock, SBlock elseblock) {
        super(line, offset, location);

        this.condition = condition;
        this.ifblock = ifblock;
        this.elseblock = elseblock;
    }

    @Override
    void analyze(Variables variables) {
        condition.expected = Definition.BOOLEAN_TYPE;
        condition.analyze(variables);
        condition = condition.cast(variables);

        if (condition.constant != null) {
            throw new IllegalArgumentException(error("Extraneous if statement."));
        }

        if (ifblock == null) {
            throw new IllegalArgumentException(error("Extraneous if statement."));
        }

        ifblock.lastSource = lastSource;
        ifblock.inLoop = inLoop;
        ifblock.lastLoop = lastLoop;

        variables.incrementScope();
        ifblock.analyze(variables);
        variables.decrementScope();

        anyContinue = ifblock.anyContinue;
        anyBreak = ifblock.anyBreak;
        statementCount = ifblock.statementCount;

        if (elseblock == null) {
            throw new IllegalArgumentException(error("Extraneous else statement."));
        }

        elseblock.lastSource = lastSource;
        elseblock.inLoop = inLoop;
        elseblock.lastLoop = lastLoop;

        variables.incrementScope();
        elseblock.analyze(variables);
        variables.decrementScope();

        methodEscape = ifblock.methodEscape && elseblock.methodEscape;
        loopEscape = ifblock.loopEscape && elseblock.loopEscape;
        allEscape = ifblock.allEscape && elseblock.allEscape;
        anyContinue |= elseblock.anyContinue;
        anyBreak |= elseblock.anyBreak;
        statementCount = Math.max(ifblock.statementCount, elseblock.statementCount);
    }

    @Override
    void write(MethodWriter writer) {
        writeDebugInfo(writer);

        Label end = new Label();
        Label fals = elseblock != null ? new Label() : end;

        condition.fals = fals;
        condition.write(writer);

        ifblock.continu = continu;
        ifblock.brake = brake;
        ifblock.write(writer);

        if (!ifblock.allEscape) {
            writer.goTo(end);
        }

        writer.mark(fals);

        elseblock.continu = continu;
        elseblock.brake = brake;
        elseblock.write(writer);

        writer.mark(end);
    }
}
