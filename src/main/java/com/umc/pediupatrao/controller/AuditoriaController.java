package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.service.AuditoriaService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AuditoriaController {
    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/auditoria")
    public String pagina(@RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) String usuario,
            Model model) {
        model.addAttribute("registros", auditoriaService.consultar(entidade, entidadeId, usuario, null));
        model.addAttribute("filtroEntidade", entidade);
        model.addAttribute("filtroEntidadeId", entidadeId);
        model.addAttribute("filtroUsuario", usuario);
        model.addAttribute("content", "auditoria :: content");
        return "layout";
    }

    @GetMapping("/api/auditoria")
    @ResponseBody
    public List<Auditoria> consultar(@RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) Integer limite) {
        return auditoriaService.consultar(entidade, entidadeId, usuario, limite);
    }
}
