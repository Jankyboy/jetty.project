//
//  ========================================================================
//  Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.component.DumpableCollection;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class FilterHolder extends Holder<Filter>
{
    private static final Logger LOG = Log.getLogger(FilterHolder.class);

    private transient Filter _filter;
    private transient Config _config;
    private transient FilterRegistration.Dynamic _registration;

    /**
     * Constructor
     */
    public FilterHolder()
    {
        this(Source.EMBEDDED);
    }

    /**
     * Constructor
     *
     * @param source the holder source
     */
    public FilterHolder(Source source)
    {
        super(source);
    }

    /**
     * Constructor
     *
     * @param filter the filter class
     */
    public FilterHolder(Class<? extends Filter> filter)
    {
        this(Source.EMBEDDED);
        setHeldClass(filter);
    }

    /**
     * Constructor for existing filter.
     *
     * @param filter the filter
     */
    public FilterHolder(Filter filter)
    {
        this(Source.EMBEDDED);
        setFilter(filter);
    }

    @Override
    public void doStart()
        throws Exception
    {
        super.doStart();

        if (!javax.servlet.Filter.class.isAssignableFrom(getHeldClass()))
        {
            String msg = getHeldClass() + " is not a javax.servlet.Filter";
            doStop();
            throw new IllegalStateException(msg);
        }
    }

    @Override
    public void initialize() throws Exception
    {
        synchronized (this)
        {
            if (_filter != null)
                return;

            super.initialize();
            _filter = getInstance();
            if (_filter == null)
            {
                try
                {
                    ServletContext context = getServletHandler().getServletContext();
                    _filter = (context != null)
                        ? context.createFilter(getHeldClass())
                        : getHeldClass().getDeclaredConstructor().newInstance();
                }
                catch (ServletException ex)
                {
                    Throwable cause = ex.getRootCause();
                    if (cause instanceof InstantiationException)
                        throw (InstantiationException)cause;
                    if (cause instanceof IllegalAccessException)
                        throw (IllegalAccessException)cause;
                    throw ex;
                }
            }
            _filter = wrap(_filter);
            _config = new Config();
            if (LOG.isDebugEnabled())
                LOG.debug("Filter.init {}", _filter);
            _filter.init(_config);
        }
    }

    @Override
    public void doStop()
        throws Exception
    {
        super.doStop();
        _config = null;
        if (_filter != null)
        {
            try
            {
                destroyInstance(_filter);
            }
            finally
            {
                _filter = null;
            }
        }
    }

    @Override
    public void destroyInstance(Object o)
    {
        if (o == null)
            return;

        Filter filter = (Filter)o;
        // destroy the wrapped filter, in case there is special behaviour
        filter.destroy();
        // need to use the unwrapped filter because lifecycle callbacks such as
        // postconstruct and predestroy are based off the classname and the wrapper
        // classes are unknown outside the ServletHolder
        getServletHandler().destroyFilter(unwrap(filter));
    }

    private Filter wrap(final Filter filter)
    {
        Filter ret = filter;
        ServletContextHandler contextHandler = getServletHandler().getServletContextHandler();
        if (contextHandler != null)
        {
            for (FilterHolder.WrapperFunction wrapperFunction : getServletHandler().getServletContextHandler().getBeans(FilterHolder.WrapperFunction.class))
            {
                ret = wrapperFunction.wrapFilter(ret);
            }
        }
        return ret;
    }

    private Filter unwrap(Filter filter)
    {
        Filter unwrapped = filter;
        while (FilterHolder.WrapperFilter.class.isAssignableFrom(unwrapped.getClass()))
        {
            unwrapped = ((FilterHolder.WrapperFilter)unwrapped).getWrappedFilter();
        }
        return unwrapped;
    }

    public synchronized void setFilter(Filter filter)
    {
        setInstance(filter);
    }

    public Filter getFilter()
    {
        return _filter;
    }

    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain)
        throws IOException, ServletException
    {
        _filter.doFilter(request, response, chain);
    }

    @Override
    public void dump(Appendable out, String indent) throws IOException
    {
        if (getInitParameters().isEmpty())
            Dumpable.dumpObjects(out, indent, this,
                _filter == null ? getHeldClass() : _filter);
        else
            Dumpable.dumpObjects(out, indent, this,
                _filter == null ? getHeldClass() : _filter,
                new DumpableCollection("initParams", getInitParameters().entrySet()));
    }

    @Override
    public String toString()
    {
        return String.format("%s@%x==%s,inst=%b,async=%b", getName(), hashCode(), getClassName(), _filter != null, isAsyncSupported());
    }

    public FilterRegistration.Dynamic getRegistration()
    {
        if (_registration == null)
            _registration = new Registration();
        return _registration;
    }

    protected class Registration extends HolderRegistration implements FilterRegistration.Dynamic
    {
        @Override
        public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... servletNames)
        {
            illegalStateIfContextStarted();
            FilterMapping mapping = new FilterMapping();
            mapping.setFilterHolder(FilterHolder.this);
            mapping.setServletNames(servletNames);
            mapping.setDispatcherTypes(dispatcherTypes);
            if (isMatchAfter)
                getServletHandler().addFilterMapping(mapping);
            else
                getServletHandler().prependFilterMapping(mapping);
        }

        @Override
        public void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... urlPatterns)
        {
            illegalStateIfContextStarted();
            FilterMapping mapping = new FilterMapping();
            mapping.setFilterHolder(FilterHolder.this);
            mapping.setPathSpecs(urlPatterns);
            mapping.setDispatcherTypes(dispatcherTypes);
            if (isMatchAfter)
                getServletHandler().addFilterMapping(mapping);
            else
                getServletHandler().prependFilterMapping(mapping);
        }

        @Override
        public Collection<String> getServletNameMappings()
        {
            FilterMapping[] mappings = getServletHandler().getFilterMappings();
            List<String> names = new ArrayList<>();
            for (FilterMapping mapping : mappings)
            {
                if (mapping.getFilterHolder() != FilterHolder.this)
                    continue;
                String[] servlets = mapping.getServletNames();
                if (servlets != null && servlets.length > 0)
                    names.addAll(Arrays.asList(servlets));
            }
            return names;
        }

        @Override
        public Collection<String> getUrlPatternMappings()
        {
            FilterMapping[] mappings = getServletHandler().getFilterMappings();
            List<String> patterns = new ArrayList<>();
            for (FilterMapping mapping : mappings)
            {
                if (mapping.getFilterHolder() != FilterHolder.this)
                    continue;
                String[] specs = mapping.getPathSpecs();
                patterns.addAll(TypeUtil.asList(specs));
            }
            return patterns;
        }
    }

    class Config extends HolderConfig implements FilterConfig
    {
        @Override
        public String getFilterName()
        {
            return getName();
        }
    }

    public interface WrapperFunction
    {
        Filter wrapFilter(Filter filter);
    }

    public static class WrapperFilter implements Filter
    {
        private final Filter _filter;

        public WrapperFilter(Filter filter)
        {
            _filter = Objects.requireNonNull(filter, "Filter cannot be null");
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException
        {
            _filter.init(filterConfig);
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
        {
            _filter.doFilter(request, response, chain);
        }

        @Override
        public void destroy()
        {
            _filter.destroy();
        }

        public Filter getWrappedFilter()
        {
            return _filter;
        }

        @Override
        public String toString()
        {
            return String.format("%s:%s", this.getClass().getSimpleName(), _filter.toString());
        }
    }
}
