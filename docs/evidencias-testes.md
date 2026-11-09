# Evidências de testes automatizados

Execução: `mvn test` (JDK 21). Gerado em 07/11/2026 a partir dos relatórios do Surefire.

**Resumo:** 86 testes, 0 falhas, 0 erros.

## Detalhamento por classe

### AutorizacaoTest

| caso de teste | resultado |
|---|---|
| auditoriaNaoAceitaEscritaNemExclusao | passou |
| clientesRespeitamPerfis | passou |
| adminSoConsultaPedidos | passou |
| atendenteNaoAplicaDescontoNemCancelaNemMudaStatus | passou |
| semTokenCsrfOPostEhRecusado | passou |
| regraDeNegocioViraRespostaComMensagem | passou |
| auditoriaSoParaAdminEGerente | passou |
| gerenteNaoCriaUsuarioNemSeAutoPromove | passou |
| adminNaoAplicaDescontoNemCancela | passou |
| usuariosSoParaAdmin | passou |
| semLoginAApiResponde401 | passou |
| gerenteAplicaDescontoECancela | passou |
| atendenteRegistraEConsultaPedidos | passou |

### DashboardApiTest

| caso de teste | resultado |
|---|---|
| rankingDevolveListaDeProdutos | passou |
| limiteNaoNumericoRetorna400 | passou |
| atendenteNaoAcessaODashboard | passou |
| parametroInvalidoRetorna400ComMensagem | passou |
| adminTambemConsulta | passou |
| semLoginRetorna401 | passou |
| apiDoDashboardEhSomenteLeitura | passou |
| gerenteConsultaOResumoComOFormatoEsperado | passou |

### TelasTest

| caso de teste | resultado |
|---|---|
| atendenteVeOsPedidosSemBotoesDeGestao | passou |
| todasAsTelasAbremSemErro | passou |
| gerenteVeBotoesDeGestaoDoPedido | passou |
| menuLateralEscondeOpcoesSemPermissao | passou |
| adminVeOsPedidosSemAcoes | passou |
| dashboardAbreParaAdminEGerenteMasNaoParaAtendente | passou |
| paginaDeAuditoriaMostraValoresAnterioresENovos | passou |
| produtosSoMostraCadastroParaGerente | passou |
| formularioDeNovoPedidoAbreParaAtendenteMasNaoParaAdmin | passou |

### FiltroDashboardTest

| caso de teste | resultado |
|---|---|
| aceitaDatasStatusETipo | passou |
| parametrosVaziosViramPadrao | passou |
| dataInicialDepoisDaFinalEhRecusada | passou |
| periodoMaiorQueUmAnoEhRecusado | passou |
| statusInvalidoEhRecusado | passou |
| formatoDeDataInvalidoEhRecusado | passou |

### PedidoRepositoryConsultaTest

| caso de teste | resultado |
|---|---|
| consultaPorPeriodoUsaIntervaloSemiAberto | passou |

### ClienteServiceTest

| caso de teste | resultado |
|---|---|
| novoClienteGeraAuditoriaDeInclusao | passou |
| semMudancaNaoGeraAuditoria | passou |
| mudancaSoNosUltimosDigitosDoTelefoneContinuaSendoDetectada | passou |
| alteracaoDeEnderecoGeraAuditoriaComValorAnteriorENovo | passou |
| alteracaoDeTelefoneNaoGuardaONumeroCompleto | passou |

### DashboardServiceTest

| caso de teste | resultado |
|---|---|
| categoriasSomamReceitaPorTipo | passou |
| pedidosPorStatusTrazTodosOsStatusComPercentual | passou |
| agrupamentoInvalidoEhRecusado | passou |
| vendasPorDiaPreencheDiasSemVendas | passou |
| rankingOrdenaPorQuantidadeEIgnoraCancelados | passou |
| pedidoAntigoSemValoresNaoQuebraOsCalculos | passou |
| periodoPadraoSaoOsUltimos30Dias | passou |
| rankingRespeitaLimiteETipo | passou |
| limiteForaDoIntervaloEhRecusado | passou |
| vendasPorHoraMostraOsPicos | passou |
| filtroDeStatusRestringeOsPedidos | passou |
| vendasPorMesAgrupaNoMes | passou |
| resumoSemPedidosDevolveZeros | passou |
| itensAntigosSemTipoUsamOCadastroDeProdutos | passou |
| resumoCalculaIndicadoresIgnorandoCancelados | passou |

### PedidoServiceTest

| caso de teste | resultado |
|---|---|
| naoCancelaPedidoRetiradoOuFinalizado | passou |
| descontoRecalculaTotalNoServidor | passou |
| statusCanceladoNaoPodeSerAlterado | passou |
| cancelarGravaDadosEAuditoriaComJustificativa | passou |
| naoCancelaDepoisDaSaida | passou |
| naoPermiteSaltarEtapas | passou |
| descontoDeVinteEhAceito | passou |
| cancelarExigeJustificativa | passou |
| descontoNaoPodeSerAplicadoEmPedidoCancelado | passou |
| fluxoCompletoComSaidaPorEntrega | passou |
| naoPermiteVoltarStatus | passou |
| criarPedidoCalculaTotalNoServidorEPreencheEntrada | passou |
| criarPedidoParaClienteInexistenteEhRecusado | passou |
| cancelarPeloEndpointDeStatusEhRecusado | passou |
| criarPedidoComQuantidadeInvalidaEhRecusado | passou |
| statusDesconhecidoEhRecusado | passou |
| descontoAcimaDeVintePorCentoEhRecusado | passou |
| naoCancelaDuasVezes | passou |
| descontoNaoPodeSerAplicadoDepoisDaSaida | passou |
| descontoZeroOuNegativoEhRecusado | passou |
| naoPermiteFinalizarPedidoEmPreparacao | passou |
| criarPedidoSemItensEhRecusado | passou |

### UsuarioServiceTest

| caso de teste | resultado |
|---|---|
| naoExcluiOProprioUsuario | passou |
| senhaCurtaEhRecusada | passou |
| senhaEhGravadaCriptografada | passou |
| adminPodeAtribuirPerfilAOutroUsuario | passou |
| naoRemoveOUltimoAdministrador | passou |
| perfilInvalidoNaCriacaoEhRecusado | passou |
| administradorNaoAlteraOProprioPerfil | passou |
