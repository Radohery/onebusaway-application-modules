package org.onebusaway.transit_data_federation_webapp.siri;

import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.onebusaway.collections.CollectionsLibrary;
import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.SiriChannelInfo;
import org.onebusaway.siri.core.SiriClient;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriClientRequestFactory;
import org.onebusaway.siri.core.SiriCoreModule;
import org.onebusaway.siri.core.SiriLibrary;
import org.onebusaway.siri.core.SiriCommon.ELogRawXmlType;
import org.onebusaway.siri.core.guice.LifecycleService;
import org.onebusaway.siri.core.handlers.SiriServiceDeliveryHandler;
import org.onebusaway.transit_data_federation.impl.realtime.siri.SiriEndpointDetails;
import org.onebusaway.transit_data_federation.impl.realtime.siri.SiriService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.ServiceDelivery;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

@Controller
public class SiriController {

  private static Logger _log = LoggerFactory.getLogger(SiriController.class);

  private SiriService _siriService;

  private ServiceDeliveryHandlerImpl _handler = new ServiceDeliveryHandlerImpl();

  private List<String> _endpoints;

  private SiriClient _client;

  private LifecycleService _lifecycleService;

  private String _clientUrl;
  
  private ELogRawXmlType _logRawXmlType;

  @Autowired
  public void setSiriService(SiriService siriService) {
    _siriService = siriService;
  }

  public void setEndpoint(String endpoint) {
    _endpoints = Arrays.asList(endpoint);
  }

  public void setEndpoints(List<String> endpoints) {
    _endpoints = endpoints;
  }

  public void setClientUrl(String clientUrl) {
    _clientUrl = clientUrl;
  }

  public void setLogRawXmlType(ELogRawXmlType logRawXmlType) {
    _logRawXmlType = logRawXmlType;
  }

  @PostConstruct
  public void start() {

    List<Module> modules = new ArrayList<Module>();
    modules.addAll(SiriCoreModule.getModules());
    Injector injector = Guice.createInjector(modules);
    
    _client = injector.getInstance(SiriClient.class);
    _lifecycleService = injector.getInstance(LifecycleService.class);

    _client.addServiceDeliveryHandler(_handler);

    if( _clientUrl != null)
      _client.setUrl(_clientUrl);
    if (_logRawXmlType != null)
      _client.setLogRawXmlType(_logRawXmlType);

    _lifecycleService.start();

    if (!CollectionsLibrary.isEmpty(_endpoints)) {

      SiriClientRequestFactory factory = new SiriClientRequestFactory();

      for (String endpoint : _endpoints) {

        Map<String, String> args = SiriLibrary.getLineAsMap(endpoint);
        SiriClientRequest request = factory.createSubscriptionRequest(args);

        SiriEndpointDetails context = new SiriEndpointDetails();

        String agencyId = args.get("AgencyId");
        if (agencyId != null)
          context.getDefaultAgencyIds().add(agencyId);

        String agencyIds = args.get("AgencyIds");
        if (agencyIds != null) {
          for (String id : agencyIds.split(","))
            context.getDefaultAgencyIds().add(id);
        }

        request.setChannelContext(context);

        _client.handleRequest(request);
      }
    }
  }

  @PreDestroy
  public void stop() {
    _client.removeServiceDeliveryHandler(_handler);
    _lifecycleService.stop();
  }

  @RequestMapping(value = "/siri.action")
  public void siri(Reader reader, Writer writer) {
    _client.handleRawRequest(reader, writer);
  }

  private class ServiceDeliveryHandlerImpl implements
      SiriServiceDeliveryHandler {

    @Override
    public void handleServiceDelivery(SiriChannelInfo channelInfo,
        ServiceDelivery serviceDelivery) {

      SiriEndpointDetails endpoint = channelInfo.getContext();

      if (endpoint == null) {
        _log.warn("could not find siri delivery info");
        return;
      }

      for (ESiriModuleType moduleType : ESiriModuleType.values()) {

        List<AbstractServiceDeliveryStructure> deliveriesForModule = SiriLibrary.getServiceDeliveriesForModule(
            serviceDelivery, moduleType);

        for (AbstractServiceDeliveryStructure deliveryForModule : deliveriesForModule) {

          _siriService.handleServiceDelivery(serviceDelivery,
              deliveryForModule, moduleType, endpoint);
        }
      }
    }
  }
}