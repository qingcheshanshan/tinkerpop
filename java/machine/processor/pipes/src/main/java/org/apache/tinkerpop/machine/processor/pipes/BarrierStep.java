/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.machine.processor.pipes;

import org.apache.tinkerpop.machine.function.BarrierFunction;
import org.apache.tinkerpop.machine.processor.pipes.util.Barrier;
import org.apache.tinkerpop.machine.processor.pipes.util.InMemoryBarrier;
import org.apache.tinkerpop.machine.traverser.Traverser;
import org.apache.tinkerpop.machine.traverser.TraverserFactory;
import org.apache.tinkerpop.machine.util.EmptyIterator;
import org.apache.tinkerpop.machine.util.IteratorUtils;

import java.util.Iterator;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
class BarrierStep<C, S, E, B> extends AbstractStep<C, S, E> {

    private final Barrier<B> barrier;
    private final BarrierFunction<C, S, E, B> barrierFunction;
    private boolean done = false;
    private Iterator<Traverser<C, E>> output = EmptyIterator.instance();
    private final TraverserFactory<C> traverserFactory;

    BarrierStep(final Step<C, ?, S> previousStep, final BarrierFunction<C, S, E, B> barrierFunction, final TraverserFactory<C> traverserFactory) {
        super(previousStep, barrierFunction);
        this.barrier = new InMemoryBarrier<>(barrierFunction.getInitialValue()); // TODO: move to strategy determination
        this.barrierFunction = barrierFunction;
        this.traverserFactory = traverserFactory;
    }

    @Override
    public Traverser<C, E> next() {
        if (!this.done) {
            while (this.previousStep.hasNext()) {
                this.barrier.update(this.barrierFunction.apply(super.previousStep.next(), this.barrier.get()));
            }
            this.done = true;
            this.output = this.barrierFunction.returnsTraversers() ?
                    (Iterator<Traverser<C, E>>) this.barrierFunction.createIterator(this.barrier.get()) :
                    IteratorUtils.map(this.barrierFunction.createIterator(this.barrier.get()),
                            e -> this.traverserFactory.create(this.barrierFunction, e));
        }
        return this.output.next();
    }

    @Override
    public boolean hasNext() {
        return this.output.hasNext() || (!this.done && this.previousStep.hasNext());
    }

    @Override
    public void reset() {
        this.barrier.reset();
        this.output = EmptyIterator.instance();
        this.done = false;
    }
}